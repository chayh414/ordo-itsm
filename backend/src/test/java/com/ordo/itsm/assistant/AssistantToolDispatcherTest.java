package com.ordo.itsm.assistant;

import com.ordo.itsm.assistant.tool.AssistantTool;
import com.ordo.itsm.assistant.tool.CreateRequestTool;
import com.ordo.itsm.assistant.tool.GetRequestTool;
import com.ordo.itsm.assistant.tool.ListMyRequestsTool;
import com.ordo.itsm.assistant.tool.ListRequestsTool;
import com.ordo.itsm.assistant.tool.ToolResult;
import com.ordo.itsm.audit.AuditAction;
import com.ordo.itsm.audit.AuditLogService;
import com.ordo.itsm.global.exception.BusinessException;
import com.ordo.itsm.global.exception.ErrorCode;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.user.UserRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 디스패처의 역할 검증·예외 처리·감사 로그 호출 패턴을 fake 로 검증 (DB 없음). */
class AssistantToolDispatcherTest {

    private final AuthUser requester = new AuthUser(1L, 10L, UserRole.REQUESTER, "r@a.demo");
    private final AuthUser operator = new AuthUser(2L, 10L, UserRole.OPERATOR, "op@sysone.demo");

    @Test
    void operator_cannotCallCreateRequest_andAccessDeniedIsAudited() {
        FakeAuditLog audit = new FakeAuditLog();
        AssistantToolDispatcher d = dispatcher(audit, failingTool(CreateRequestTool.NAME));

        ToolResult res = d.dispatch(operator, CreateRequestTool.NAME, Map.of("text", "..."));

        assertFalse(res.ok(), "역할에 없는 도구 호출은 거부");
        assertTrue(audit.actions.contains(AuditAction.ACCESS_DENIED), "ACCESS_DENIED 감사 로그 있어야 함");
        // 거부되면 도구 실행 자체가 안 일어나므로 ASSISTANT_TOOL_CALLED 는 기록되지 않는다 (ACCESS_DENIED 로 충분).
    }

    @Test
    void requester_canCallCreateRequest_andToolCallIsAudited() {
        FakeAuditLog audit = new FakeAuditLog();
        AssistantToolDispatcher d = dispatcher(audit, okTool(CreateRequestTool.NAME, "created-ok"));

        ToolResult res = d.dispatch(requester, CreateRequestTool.NAME, Map.of("text", "x"));

        assertTrue(res.ok());
        assertEquals("created-ok", res.llmText());
        assertTrue(audit.actions.contains(AuditAction.ASSISTANT_TOOL_CALLED));
    }

    @Test
    void unknownTool_returnsError() {
        FakeAuditLog audit = new FakeAuditLog();
        AssistantToolDispatcher d = dispatcher(audit, okTool(ListMyRequestsTool.NAME, "ok"));

        ToolResult res = d.dispatch(requester, "nope", Map.of());

        assertFalse(res.ok());
        assertTrue(audit.actions.contains(AuditAction.ACCESS_DENIED), "역할 세트 밖 도구는 ACCESS_DENIED 로 처리");
    }

    @Test
    void businessException_isConvertedToError_andStillAudited() {
        FakeAuditLog audit = new FakeAuditLog();
        AssistantTool failing = new AssistantTool() {
            @Override public String name() { return GetRequestTool.NAME; }
            @Override public String description() { return ""; }
            @Override public Map<String, Object> parametersSchema() { return Map.of(); }
            @Override public ToolResult execute(AuthUser u, Map<String, Object> a) {
                throw new BusinessException(ErrorCode.TICKET_NOT_FOUND, "티켓 없음");
            }
        };
        AssistantToolDispatcher d = dispatcher(audit, failing);

        ToolResult res = d.dispatch(requester, GetRequestTool.NAME, Map.of("ticketNo", "ORD-x"));

        assertFalse(res.ok());
        assertTrue(res.errorMessage().contains("티켓 없음"));
        assertTrue(audit.actions.contains(AuditAction.ASSISTANT_TOOL_CALLED),
                "실패해도 호출 사실은 감사에 남아야 함");
    }

    @Test
    void runtimeException_isSwallowed_andError() {
        FakeAuditLog audit = new FakeAuditLog();
        AssistantTool blowup = new AssistantTool() {
            @Override public String name() { return ListMyRequestsTool.NAME; }
            @Override public String description() { return ""; }
            @Override public Map<String, Object> parametersSchema() { return Map.of(); }
            @Override public ToolResult execute(AuthUser u, Map<String, Object> a) {
                throw new RuntimeException("boom");
            }
        };
        AssistantToolDispatcher d = dispatcher(audit, blowup);

        ToolResult res = d.dispatch(requester, ListMyRequestsTool.NAME, Map.of());

        assertFalse(res.ok());
        assertFalse(res.errorMessage().contains("boom"), "원 예외 메시지가 사용자에게 노출되면 안 됨");
        assertTrue(audit.actions.contains(AuditAction.ASSISTANT_TOOL_CALLED));
    }

    // --- helpers ---

    private AssistantToolDispatcher dispatcher(FakeAuditLog audit, AssistantTool tool) {
        // 레지스트리는 실제 구현을 쓰되, 도구 하나만 넣고 나머지는 fake 로 채워 역할 세트 전체가 매핑되도록 한다.
        List<AssistantTool> all = new ArrayList<>();
        for (String n : List.of(CreateRequestTool.NAME, ListMyRequestsTool.NAME,
                ListRequestsTool.NAME, GetRequestTool.NAME)) {
            all.add(n.equals(tool.name()) ? tool : okTool(n, ""));
        }
        AssistantToolRegistry registry = new AssistantToolRegistry(all);
        return new AssistantToolDispatcher(registry, audit);
    }

    private static AssistantTool okTool(String name, String text) {
        return new AssistantTool() {
            @Override public String name() { return name; }
            @Override public String description() { return ""; }
            @Override public Map<String, Object> parametersSchema() { return Map.of(); }
            @Override public ToolResult execute(AuthUser u, Map<String, Object> a) {
                return ToolResult.ok(text, null);
            }
        };
    }

    private static AssistantTool failingTool(String name) {
        return new AssistantTool() {
            @Override public String name() { return name; }
            @Override public String description() { return ""; }
            @Override public Map<String, Object> parametersSchema() { return Map.of(); }
            @Override public ToolResult execute(AuthUser u, Map<String, Object> a) {
                throw new AssertionError("권한 없는 호출이 도구까지 도달하면 안 됨");
            }
        };
    }

    /** AuditLogService 를 상속해 record/recordIndependently 호출 사실만 기록하는 fake. */
    private static class FakeAuditLog extends AuditLogService {
        final List<AuditAction> actions = new ArrayList<>();
        FakeAuditLog() { super(null, null); }
        @Override public void record(AuditAction action, AuthUser actor, Long tenantId, Long ticketId,
                                     Map<String, ?> before, Map<String, ?> after) {
            actions.add(action);
        }
        @Override public void recordIndependently(AuditAction action, AuthUser actor, Long tenantId, Long ticketId,
                                                   Map<String, ?> before, Map<String, ?> after) {
            actions.add(action);
        }
    }
}
