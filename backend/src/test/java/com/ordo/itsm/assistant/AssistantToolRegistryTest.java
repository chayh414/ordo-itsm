package com.ordo.itsm.assistant;

import com.ordo.itsm.assistant.tool.AssistantTool;
import com.ordo.itsm.assistant.tool.CreateRequestTool;
import com.ordo.itsm.assistant.tool.GetRequestTool;
import com.ordo.itsm.assistant.tool.ListMyRequestsTool;
import com.ordo.itsm.assistant.tool.ListRequestsTool;
import com.ordo.itsm.assistant.tool.ToolResult;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.user.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 역할 → 도구 세트 매핑 단위 테스트 (Spring 없이) */
class AssistantToolRegistryTest {

    private final AssistantToolRegistry registry = new AssistantToolRegistry(List.of(
            fakeTool(CreateRequestTool.NAME),
            fakeTool(ListMyRequestsTool.NAME),
            fakeTool(ListRequestsTool.NAME),
            fakeTool(GetRequestTool.NAME)
    ));

    @Test
    void requester_hasThreeCustomerTools() {
        Set<String> names = toolNamesFor(UserRole.REQUESTER);
        assertEquals(Set.of(CreateRequestTool.NAME, ListMyRequestsTool.NAME, GetRequestTool.NAME), names);
    }

    @Test
    void customerAdmin_hasSameAsRequester() {
        assertEquals(toolNamesFor(UserRole.REQUESTER), toolNamesFor(UserRole.CUSTOMER_ADMIN));
    }

    @Test
    void operator_hasOnlyListAndGet_noCreate() {
        Set<String> names = toolNamesFor(UserRole.OPERATOR);
        assertEquals(Set.of(ListRequestsTool.NAME, GetRequestTool.NAME), names);
        assertFalse(names.contains(CreateRequestTool.NAME));
    }

    @Test
    void approver_sameAsOperator() {
        assertEquals(toolNamesFor(UserRole.OPERATOR), toolNamesFor(UserRole.APPROVER));
    }

    @Test
    void opsAdmin_sameAsOperator() {
        assertEquals(toolNamesFor(UserRole.OPERATOR), toolNamesFor(UserRole.OPS_ADMIN));
    }

    @Test
    void isAllowed_matchesToolList() {
        assertTrue(registry.isAllowed(UserRole.REQUESTER, CreateRequestTool.NAME));
        assertFalse(registry.isAllowed(UserRole.OPERATOR, CreateRequestTool.NAME),
                "공급사 측은 create_request 호출 불가");
        assertTrue(registry.isAllowed(UserRole.OPERATOR, ListRequestsTool.NAME));
        assertFalse(registry.isAllowed(UserRole.REQUESTER, ListRequestsTool.NAME),
                "요청자는 list_requests 호출 불가");
    }

    private Set<String> toolNamesFor(UserRole role) {
        return registry.toolsFor(role).stream().map(AssistantTool::name).collect(Collectors.toSet());
    }

    private static AssistantTool fakeTool(String name) {
        return new AssistantTool() {
            @Override public String name() { return name; }
            @Override public String description() { return ""; }
            @Override public Map<String, Object> parametersSchema() { return Map.of(); }
            @Override public ToolResult execute(AuthUser u, Map<String, Object> a) { return ToolResult.ok("", null); }
        };
    }
}
