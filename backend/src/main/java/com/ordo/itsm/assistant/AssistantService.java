package com.ordo.itsm.assistant;

import com.ordo.itsm.assistant.dto.ChatRequest;
import com.ordo.itsm.assistant.dto.ChatResponse;
import com.ordo.itsm.assistant.engine.AssistantLlmClient;
import com.ordo.itsm.assistant.engine.AssistantLlmException;
import com.ordo.itsm.assistant.engine.AssistantReply;
import com.ordo.itsm.assistant.engine.ChatMessage;
import com.ordo.itsm.assistant.engine.ToolCall;
import com.ordo.itsm.assistant.engine.ToolCallRecovery;
import com.ordo.itsm.assistant.engine.ToolSpec;
import com.ordo.itsm.assistant.tool.AssistantTool;
import com.ordo.itsm.assistant.tool.ToolResult;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.triage.SensitiveDataMasker;
import com.ordo.itsm.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 어시스턴트의 대화 루프.
 * 1) 사용자 메시지 마스킹 → 2) 역할별 tools 와 함께 LLM 호출
 * 3) tool_calls 있으면 dispatcher 로 실행 → tool 결과 되돌려주기
 * 4) tool_calls 가 없고 content 에 함수 호출 텍스트가 있으면 복구해서 실행 (ToolCallRecovery)
 * 5) 자연어 답변이 나올 때까지 최대 maxIterations 회 반복. 상한 초과 시 안전한 폴백 메시지.
 *
 * 사용자에게 돌려주는 최종 reply content 에는 함수 호출 문자열이 남지 않도록 strip 를 거친다.
 *
 * 과거 history 는 서버에 저장하지 않는다. 클라이언트가 매 턴 재전송 (서버는 20개까지만 수용).
 * history 의 role=tool 은 신뢰할 수 없으므로 user/assistant 만 받아들인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final int HISTORY_MAX = 20;

    private final AssistantLlmClient llm;
    private final AssistantToolRegistry registry;
    private final AssistantToolDispatcher dispatcher;
    private final SensitiveDataMasker masker;
    private final JsonMapper jsonMapper;

    @Value("${assistant.max-tool-iterations:3}")
    private int maxIterations;

    public ChatResponse chat(AuthUser user, ChatRequest request) {
        List<AssistantTool> tools = registry.toolsFor(user.role());
        if (tools.isEmpty()) {
            return new ChatResponse(
                    "현재 역할(" + user.role() + ")에서는 어시스턴트가 사용할 수 있는 도구가 없습니다.",
                    List.of(), List.of(), false);
        }
        List<ToolSpec> toolSpecs = tools.stream()
                .map(t -> new ToolSpec(t.name(), t.description(), t.parametersSchema()))
                .toList();
        Set<String> allowedToolNames = tools.stream()
                .map(AssistantTool::name)
                .collect(Collectors.toUnmodifiableSet());

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPromptFor(user.role())));
        for (ChatRequest.HistoryMessage h : safeHistory(request.history())) {
            String content = h.content() == null ? "" : h.content();
            if ("user".equals(h.role())) {
                messages.add(ChatMessage.user(masker.mask(content)));
            } else if ("assistant".equals(h.role())) {
                messages.add(ChatMessage.assistant(content, null));
            }
            // role=tool / 그 외는 무시 (클라이언트가 조작한 tool 결과 주입 방지)
        }
        messages.add(ChatMessage.user(masker.mask(request.message())));

        List<ChatResponse.Card> cards = new ArrayList<>();
        List<ChatResponse.ToolCallSummary> callLog = new ArrayList<>();

        for (int i = 0; i < maxIterations; i++) {
            AssistantReply reply;
            try {
                reply = llm.chat(messages, toolSpecs);
            } catch (AssistantLlmException e) {
                log.warn("[Assistant] LLM 호출 실패: {}", e.getMessage());
                return new ChatResponse(
                        "지금 AI 서버와 연결이 원활하지 않아요. 잠시 뒤 다시 시도해 주세요.",
                        cards, callLog, false);
            }

            List<ToolCall> effectiveCalls = reply.toolCalls();
            boolean recovered = false;
            String assistantContent = reply.content();

            // tool_calls 가 비어있으면 content 에서 복구 시도 (노트북 Ollama 컨텍스트 압박 시 잦음)
            if (effectiveCalls == null || effectiveCalls.isEmpty()) {
                List<ToolCallRecovery.Recovered> rc =
                        ToolCallRecovery.parse(reply.content(), allowedToolNames, jsonMapper);
                if (!rc.isEmpty()) {
                    recovered = true;
                    effectiveCalls = new ArrayList<>();
                    for (ToolCallRecovery.Recovered r : rc) {
                        String id = "rec_" + UUID.randomUUID().toString().substring(0, 8);
                        effectiveCalls.add(new ToolCall(id, r.name(), r.arguments()));
                    }
                    // 복구가 성공했으면 content 에서 호출 문자열은 제거 (남은 자연어만 history 에 남긴다)
                    String stripped = ToolCallRecovery.strip(reply.content(), allowedToolNames, jsonMapper);
                    assistantContent = stripped.isBlank() ? null : stripped;
                    log.info("[Assistant] 텍스트 도구 호출 복구 {}건 (user={})",
                            effectiveCalls.size(), user.userId());
                }
            }

            messages.add(ChatMessage.assistant(assistantContent, effectiveCalls));

            if (effectiveCalls == null || effectiveCalls.isEmpty()) {
                // tool 호출 없음 & 복구 없음 → 자연어 답변으로 종료.
                // content 에 혹시 남은 함수 호출 흔적을 한번 더 strip 해서 화면 보호.
                String clean = ToolCallRecovery.strip(reply.content(), allowedToolNames, jsonMapper);
                if (clean.isBlank()) {
                    clean = cards.isEmpty()
                            ? "요청을 이해하지 못했어요. 조금만 더 설명해 주실래요?"
                            : "요청하신 작업을 처리했어요. 아래 결과를 확인해 주세요.";
                }
                return new ChatResponse(clean, cards, callLog, false);
            }

            for (ToolCall call : effectiveCalls) {
                ToolResult result = dispatcher.dispatch(user, call.name(), call.arguments(), recovered);
                messages.add(ChatMessage.tool(call.id(), result.llmText()));
                if (result.cardData() != null) {
                    cards.add(new ChatResponse.Card(call.name(), result.cardData()));
                }
                callLog.add(new ChatResponse.ToolCallSummary(
                        call.name(), call.arguments(), result.ok(), result.errorMessage()));
            }
        }

        // 상한 초과 — 더 이상 모델에 재질문하지 않고 안전한 폴백.
        return new ChatResponse(
                "요청하신 작업을 처리했어요. 결과는 아래 카드를 확인해 주세요.",
                cards, callLog, true);
    }

    private static List<ChatRequest.HistoryMessage> safeHistory(List<ChatRequest.HistoryMessage> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        int from = Math.max(0, raw.size() - HISTORY_MAX);
        return raw.subList(from, raw.size());
    }

    private static String systemPromptFor(UserRole role) {
        return """
                너는 시스원 사내 IT 운영 어시스턴트다. 다음 원칙을 지킨다.
                1) 사용자가 '등록·접수·요청해줘'를 명확히 요구할 때만 create_request 를 호출한다.
                   "어떻게 해야 해?" "방법 알려줘" 같은 질문엔 도구 없이 설명으로만 답한다.
                2) 과거 요청 목록을 묻는 질문에는 list 계열 도구를 쓴다.
                3) 특정 티켓번호(ORD-YYYYMMDD-NNNN)의 상태를 묻는 질문엔 get_request 를 호출하고,
                   티켓번호는 사용자 원문의 값을 그대로 전달한다. 지어내지 않는다.
                4) 승인·상태 변경·실행 같은 조작 도구는 존재하지 않는다. 요청받아도 하지 못한다고 안내한다.
                5) 도구 호출은 반드시 tool_calls 로 보낸다. 답변 본문에 create_request("…") 같은
                   함수 호출 문자열을 쓰지 않는다. 도구 호출 뒤 결과는 사용자 언어로 짧게(2~3문장) 요약한다.
                역할: """ + role.name();
    }
}
