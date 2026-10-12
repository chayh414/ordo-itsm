package com.ordo.itsm.assistant;

import com.ordo.itsm.assistant.tool.AssistantTool;
import com.ordo.itsm.assistant.tool.ToolResult;
import com.ordo.itsm.audit.AuditAction;
import com.ordo.itsm.audit.AuditLogService;
import com.ordo.itsm.global.exception.BusinessException;
import com.ordo.itsm.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 어시스턴트의 tool_call 을 받아서 역할 검증 → 도구 실행 → 감사 로그까지 담당한다.
 * BusinessException/RuntimeException 은 ToolResult.error 로 변환해 안전하게 LLM 에 되돌려준다.
 *
 * recovered=true 는 "LLM 이 tool_calls 를 안 보내서 content 텍스트에서 복구된 호출" 이라는 뜻.
 * 감사 로그에 "텍스트 도구 호출 복구" 마커가 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantToolDispatcher {

    private final AssistantToolRegistry registry;
    private final AuditLogService auditLogService;

    public ToolResult dispatch(AuthUser user, String toolName, Map<String, Object> args) {
        return dispatch(user, toolName, args, false);
    }

    public ToolResult dispatch(AuthUser user, String toolName, Map<String, Object> args, boolean recovered) {
        if (!registry.isAllowed(user.role(), toolName)) {
            auditLogService.recordIndependently(AuditAction.ACCESS_DENIED, user, user.tenantId(), null, null,
                    Map.of("reason", "ASSISTANT_TOOL_NOT_ALLOWED", "tool", toolName, "role", user.role().name(),
                            "recovered", recovered));
            return ToolResult.error("요청한 도구를 사용할 권한이 없거나, 지원하지 않는 도구입니다: " + toolName);
        }

        AssistantTool tool = registry.find(toolName);
        if (tool == null) {
            return ToolResult.error("알 수 없는 도구: " + toolName);
        }

        ToolResult result;
        try {
            result = tool.execute(user, args == null ? Map.of() : args);
        } catch (BusinessException be) {
            log.warn("[Assistant] 도구 실행 실패 (tool={}, user={}, recovered={}): {}",
                    toolName, user.userId(), recovered, be.getMessage());
            result = ToolResult.error(be.getMessage());
        } catch (RuntimeException e) {
            log.warn("[Assistant] 도구 실행 예외 (tool={}, user={}, recovered={})",
                    toolName, user.userId(), recovered, e);
            result = ToolResult.error("도구 실행 중 오류가 발생했습니다.");
        }

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("tool", toolName);
        after.put("args", args == null ? Map.of() : args);
        after.put("ok", result.ok());
        if (!result.ok()) after.put("errorMessage", result.errorMessage());
        if (recovered) {
            after.put("recovered", true);
            after.put("note", "텍스트 도구 호출 복구");
        }
        auditLogService.record(AuditAction.ASSISTANT_TOOL_CALLED, user, user.tenantId(), null, null, after);

        return result;
    }
}
