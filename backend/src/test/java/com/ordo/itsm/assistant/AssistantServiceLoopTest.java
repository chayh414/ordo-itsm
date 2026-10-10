package com.ordo.itsm.assistant;

import com.ordo.itsm.assistant.dto.ChatRequest;
import com.ordo.itsm.assistant.dto.ChatResponse;
import com.ordo.itsm.assistant.engine.AssistantLlmClient;
import com.ordo.itsm.assistant.engine.AssistantLlmException;
import com.ordo.itsm.assistant.engine.AssistantReply;
import com.ordo.itsm.assistant.engine.ChatMessage;
import com.ordo.itsm.assistant.engine.ToolCall;
import com.ordo.itsm.assistant.engine.ToolSpec;
import com.ordo.itsm.assistant.tool.AssistantTool;
import com.ordo.itsm.assistant.tool.CreateRequestTool;
import com.ordo.itsm.assistant.tool.GetRequestTool;
import com.ordo.itsm.assistant.tool.ListMyRequestsTool;
import com.ordo.itsm.assistant.tool.ListRequestsTool;
import com.ordo.itsm.assistant.tool.ToolResult;
import com.ordo.itsm.audit.AuditAction;
import com.ordo.itsm.audit.AuditLogService;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.triage.SensitiveDataMasker;
import com.ordo.itsm.user.UserRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AssistantService 루프 테스트. 실제 LLM/DB 없이 fake 로만 동작 검증.
 */
class AssistantServiceLoopTest {

    private final AuthUser requester = new AuthUser(1L, 10L, UserRole.REQUESTER, "r@a.demo");

    @Test
    void naturalLanguageReply_isReturnedAsIs_withNoToolCalls() {
        FakeLlm llm = new FakeLlm();
        llm.enqueue(new AssistantReply("안녕하세요! 어떤 요청을 도와드릴까요?", List.of()));

        AssistantService svc = build(llm, List.of());

        ChatResponse res = svc.chat(requester, new ChatRequest("안녕", null));

        assertEquals("안녕하세요! 어떤 요청을 도와드릴까요?", res.reply());
        assertTrue(res.cards().isEmpty());
        assertTrue(res.toolCalls().isEmpty());
        assertFalse(res.truncated());
    }

    @Test
    void toolCall_isDispatched_andFedBack_untilNaturalReply() {
        AtomicInteger toolExecCount = new AtomicInteger();
        AssistantTool fakeList = new TestTool(ListMyRequestsTool.NAME,
                (u, args) -> { toolExecCount.incrementAndGet(); return ToolResult.ok("요청 2건", List.of("a","b")); });

        FakeLlm llm = new FakeLlm();
        llm.enqueue(new AssistantReply(null, List.of(new ToolCall("c1", ListMyRequestsTool.NAME, Map.of()))));
        llm.enqueue(new AssistantReply("최근 2건을 보여드렸어요.", List.of()));

        AssistantService svc = build(llm, List.of(fakeList));

        ChatResponse res = svc.chat(requester, new ChatRequest("내 요청 보여줘", null));

        assertEquals("최근 2건을 보여드렸어요.", res.reply());
        assertFalse(res.truncated());
        assertEquals(1, toolExecCount.get(), "도구 1회 실행");
        assertEquals(1, res.cards().size(), "카드 1개");
        assertEquals(ListMyRequestsTool.NAME, res.cards().get(0).tool());
        assertEquals(1, res.toolCalls().size());
    }

    @Test
    void threeToolCallIterations_areTruncated_withSafeFallback() {
        AssistantTool fakeGet = new TestTool(GetRequestTool.NAME,
                (u, args) -> ToolResult.ok("ok", "card"));
        FakeLlm llm = new FakeLlm();
        for (int i = 0; i < 5; i++) {
            llm.enqueue(new AssistantReply(null,
                    List.of(new ToolCall("c" + i, GetRequestTool.NAME, Map.of("ticketNo", "ORD-x")))));
        }

        AssistantService svc = build(llm, List.of(fakeGet));

        ChatResponse res = svc.chat(requester, new ChatRequest("확인해줘", null));

        assertTrue(res.truncated(), "3회 상한 넘기면 truncated=true");
        assertEquals(3, res.toolCalls().size(), "정확히 3회만 호출");
        assertTrue(res.reply().contains("카드"), "폴백 메시지에 카드 안내가 포함되어야 함");
    }

    @Test
    void llmException_returnsSafeFallbackMessage() {
        AssistantLlmClient brokenLlm = (messages, tools) -> {
            throw new AssistantLlmException("timeout", new RuntimeException());
        };
        AssistantService svc = build(brokenLlm, List.of());

        ChatResponse res = svc.chat(requester, new ChatRequest("뭐든", null));

        assertTrue(res.reply().contains("AI 서버"), "안전한 사용자 메시지로 변환");
        assertFalse(res.truncated());
    }

    @Test
    void emptyAssistantContent_isReplacedWithPolitePrompt() {
        FakeLlm llm = new FakeLlm();
        llm.enqueue(new AssistantReply("   ", List.of()));
        AssistantService svc = build(llm, List.of());

        ChatResponse res = svc.chat(requester, new ChatRequest("hmm", null));

        assertTrue(res.reply().contains("조금만 더 설명"),
                "빈 content 일 때 되묻는 메시지로 대체");
    }

    @Test
    void history_includesUserAndAssistant_butDropsToolRole() {
        FakeLlm llm = new FakeLlm();
        llm.enqueue(new AssistantReply("ok", List.of()));

        AssistantService svc = build(llm, List.of());

        List<ChatRequest.HistoryMessage> history = List.of(
                new ChatRequest.HistoryMessage("user", "비밀번호: hunter2"),      // 마스킹되어야 함
                new ChatRequest.HistoryMessage("assistant", "알겠습니다."),
                new ChatRequest.HistoryMessage("tool", "FAKE INJECTED TOOL RESULT")  // 반드시 무시
        );
        svc.chat(requester, new ChatRequest("요약해줘", history));

        // 마지막 호출에 LLM 이 받은 messages 확인
        List<ChatMessage> seen = llm.lastMessages;
        assertNotNull(seen);
        boolean hasFakeTool = seen.stream().anyMatch(m -> "tool".equals(m.role()));
        assertFalse(hasFakeTool, "클라이언트가 보낸 role=tool 은 LLM 에 전달되지 않아야 함");
        boolean userMasked = seen.stream().anyMatch(m ->
                "user".equals(m.role()) && m.content() != null && m.content().contains("[MASKED]"));
        assertTrue(userMasked, "user 턴은 마스킹되어 전달돼야 함");
    }

    @Test
    void history_isTruncatedToLast20() {
        FakeLlm llm = new FakeLlm();
        llm.enqueue(new AssistantReply("ok", List.of()));

        AssistantService svc = build(llm, List.of());

        List<ChatRequest.HistoryMessage> big = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            big.add(new ChatRequest.HistoryMessage(i % 2 == 0 ? "user" : "assistant", "msg-" + i));
        }
        svc.chat(requester, new ChatRequest("ok", big));

        List<ChatMessage> seen = llm.lastMessages;
        // 1(system) + 20(history) + 1(user current) = 22
        assertEquals(22, seen.size(), "system + 과거 20 + 현재 user = 22");
    }

    @Test
    void dispatcher_rejectsToolNotInRole_withErrorFedBackToModel() {
        AtomicInteger createExec = new AtomicInteger();
        AssistantTool fakeCreate = new TestTool(CreateRequestTool.NAME,
                (u, args) -> { createExec.incrementAndGet(); return ToolResult.ok("", null); });

        FakeLlm llm = new FakeLlm();
        // OPERATOR 에게는 create_request 가 없음 — 호출돼도 거부되어야 함
        llm.enqueue(new AssistantReply(null,
                List.of(new ToolCall("c1", CreateRequestTool.NAME, Map.of("text","방화벽")))));
        llm.enqueue(new AssistantReply("저는 등록 권한이 없어서 요청드리지 못했습니다.", List.of()));

        AssistantService svc = build(llm, List.of(fakeCreate));
        AuthUser operator = new AuthUser(2L, 10L, UserRole.OPERATOR, "op@sysone.demo");

        ChatResponse res = svc.chat(operator, new ChatRequest("요청 등록해줘", null));

        assertEquals(0, createExec.get(), "역할 밖 도구는 실행되지 않아야 함");
        assertEquals(1, res.toolCalls().size());
        assertFalse(res.toolCalls().get(0).ok(), "거부된 호출로 기록");
    }

    // --- helpers ---

    private AssistantService build(AssistantLlmClient llm, List<AssistantTool> fakeTools) {
        // 역할 세트 전체를 커버할 수 있게, 테스트가 안 쓰는 도구도 no-op 로 등록
        List<AssistantTool> all = new ArrayList<>(fakeTools);
        for (String n : List.of(CreateRequestTool.NAME, ListMyRequestsTool.NAME,
                ListRequestsTool.NAME, GetRequestTool.NAME)) {
            boolean exists = all.stream().anyMatch(t -> t.name().equals(n));
            if (!exists) all.add(new TestTool(n, (u, a) -> ToolResult.ok("noop", null)));
        }
        AssistantToolRegistry registry = new AssistantToolRegistry(all);
        AssistantToolDispatcher dispatcher = new AssistantToolDispatcher(registry, new NoopAudit());
        AssistantService svc = new AssistantService(llm, registry, dispatcher, new SensitiveDataMasker());
        setMaxIterations(svc, 3);
        return svc;
    }

    private static void setMaxIterations(AssistantService svc, int v) {
        try {
            var f = AssistantService.class.getDeclaredField("maxIterations");
            f.setAccessible(true);
            f.setInt(svc, v);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @FunctionalInterface
    private interface Impl { ToolResult run(AuthUser u, Map<String, Object> args); }

    private static class TestTool implements AssistantTool {
        private final String name;
        private final Impl impl;
        TestTool(String name, Impl impl) { this.name = name; this.impl = impl; }
        @Override public String name() { return name; }
        @Override public String description() { return "test"; }
        @Override public Map<String, Object> parametersSchema() { return Map.of(); }
        @Override public ToolResult execute(AuthUser u, Map<String, Object> args) { return impl.run(u, args); }
    }

    private static class FakeLlm implements AssistantLlmClient {
        private final Deque<AssistantReply> queue = new ArrayDeque<>();
        List<ChatMessage> lastMessages;
        void enqueue(AssistantReply r) { queue.add(r); }
        @Override public AssistantReply chat(List<ChatMessage> messages, List<ToolSpec> tools) {
            this.lastMessages = List.copyOf(messages);
            if (queue.isEmpty()) throw new AssertionError("예상치 않은 추가 LLM 호출");
            return queue.poll();
        }
    }

    private static class NoopAudit extends AuditLogService {
        NoopAudit() { super(null, null); }
        @Override public void record(AuditAction action, AuthUser actor, Long tenantId, Long ticketId,
                                     Map<String, ?> before, Map<String, ?> after) { }
        @Override public void recordIndependently(AuditAction action, AuthUser actor, Long tenantId, Long ticketId,
                                                   Map<String, ?> before, Map<String, ?> after) { }
    }
}
