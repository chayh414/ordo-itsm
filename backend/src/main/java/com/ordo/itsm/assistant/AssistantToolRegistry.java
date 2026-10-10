package com.ordo.itsm.assistant;

import com.ordo.itsm.assistant.tool.AssistantTool;
import com.ordo.itsm.assistant.tool.CreateRequestTool;
import com.ordo.itsm.assistant.tool.GetRequestTool;
import com.ordo.itsm.assistant.tool.ListMyRequestsTool;
import com.ordo.itsm.assistant.tool.ListRequestsTool;
import com.ordo.itsm.user.UserRole;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 역할 → 허용 도구 매핑과 이름 → 도구 레지스트리.
 * 역할에 없는 도구는 LLM 노출 자체를 안 하고(toolsFor), dispatcher 가 호출돼도 거부한다.
 */
@Component
public class AssistantToolRegistry {

    /** 고객사 측(요청자/고객사관리자) 도구 세트. */
    private static final Set<String> CUSTOMER_TOOLS = Set.of(
            CreateRequestTool.NAME, ListMyRequestsTool.NAME, GetRequestTool.NAME);

    /** 공급사 측(운영/승인/운영관리자) 도구 세트. 등록 도구 없음. */
    private static final Set<String> PROVIDER_TOOLS = Set.of(
            ListRequestsTool.NAME, GetRequestTool.NAME);

    private static final Map<UserRole, Set<String>> BY_ROLE;
    static {
        Map<UserRole, Set<String>> map = new LinkedHashMap<>();
        map.put(UserRole.REQUESTER, CUSTOMER_TOOLS);
        map.put(UserRole.CUSTOMER_ADMIN, CUSTOMER_TOOLS);
        map.put(UserRole.OPERATOR, PROVIDER_TOOLS);
        map.put(UserRole.APPROVER, PROVIDER_TOOLS);
        map.put(UserRole.OPS_ADMIN, PROVIDER_TOOLS);
        BY_ROLE = Map.copyOf(map);
    }

    private final Map<String, AssistantTool> byName;

    public AssistantToolRegistry(List<AssistantTool> tools) {
        this.byName = tools.stream().collect(Collectors.toUnmodifiableMap(AssistantTool::name, Function.identity()));
    }

    /** 해당 역할이 호출할 수 있는 도구들 (LLM 에 노출할 목록). */
    public List<AssistantTool> toolsFor(UserRole role) {
        Set<String> allowed = BY_ROLE.getOrDefault(role, Set.of());
        return allowed.stream().map(byName::get).filter(t -> t != null).toList();
    }

    /** 역할이 특정 도구를 호출할 권한이 있는지. */
    public boolean isAllowed(UserRole role, String toolName) {
        return BY_ROLE.getOrDefault(role, Set.of()).contains(toolName);
    }

    /** 이름으로 도구 조회. 없으면 null. */
    public AssistantTool find(String name) {
        return byName.get(name);
    }
}
