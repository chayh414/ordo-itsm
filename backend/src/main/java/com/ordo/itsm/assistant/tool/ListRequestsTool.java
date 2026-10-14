package com.ordo.itsm.assistant.tool;

import com.ordo.itsm.global.common.PageResponse;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.tenant.Tenant;
import com.ordo.itsm.tenant.TenantAccessService;
import com.ordo.itsm.tenant.TenantRepository;
import com.ordo.itsm.tenant.TenantScope;
import com.ordo.itsm.ticket.TicketService;
import com.ordo.itsm.ticket.TicketStatus;
import com.ordo.itsm.ticket.dto.TicketSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 공급사 측(OPERATOR/APPROVER/OPS_ADMIN) 전용 — TenantScope 범위의 티켓 목록.
 * 다른 사람 DRAFT 는 숨겨진다.
 *
 * 사용자가 특정 고객사를 지정하면 tenant 인자로 좁힌다. 범위 밖 고객사는 빈 결과 + 안내.
 */
@Component
@RequiredArgsConstructor
public class ListRequestsTool implements AssistantTool {

    public static final String NAME = "list_requests";
    private static final int PAGE_SIZE = 10;

    private final TicketService ticketService;
    private final TenantRepository tenantRepository;
    private final TenantAccessService tenantAccessService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "내가 접근 가능한 고객사의 요청 목록을 최신순 10건 조회한다. 공급사 담당자 전용."
                + " 사용자가 상태(예: 보완 대기, 접수, 완료)를 말하면 status 인자를 반드시 넣을 것."
                + " 사용자가 특정 고객사를 지정하면 tenant 인자에 그 이름 또는 코드를 넣을 것.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("status", Map.of(
                "type", "string",
                "enum", Arrays.stream(TicketStatus.values()).map(Enum::name).toList(),
                "description", "티켓 상태 필터 (선택). INFO_REQUIRED=보완 대기, SUBMITTED=접수됨 등."
        ));
        properties.put("tenant", Map.of(
                "type", "string",
                "description", "고객사 이름 또는 코드 (선택). 예: 'A대학교' 혹은 'A-UNIV'."
        ));
        return Map.of(
                "type", "object",
                "properties", properties,
                "additionalProperties", false
        );
    }

    @Override
    public ToolResult execute(AuthUser user, Map<String, Object> args) {
        TicketStatus status = ListMyRequestsTool.parseStatus(args);
        String tenantKey = asString(args, "tenant");

        TenantScope scope = tenantAccessService.scopeOf(user);
        String tenantLabel = null;

        if (tenantKey != null && !tenantKey.isBlank()) {
            Tenant tenant = tenantRepository.findByCodeOrName(tenantKey.trim()).orElse(null);
            if (tenant == null || !scope.includes(tenant.getId())) {
                PageResponse<TicketSummaryResponse> empty = PageResponse.empty(
                        PageRequest.of(0, PAGE_SIZE));
                String msg = "조건: " + tenantKey + " · 담당 범위 밖\n"
                        + "요청하신 '" + tenantKey + "' 은(는) 담당 고객사가 아닙니다.";
                return ToolResult.ok(msg, empty);
            }
            // 범위 안 — 그 테넌트로만 좁혀 조회.
            scope = TenantScope.of(java.util.Set.of(tenant.getId()));
            tenantLabel = tenant.getName();
        }

        PageResponse<TicketSummaryResponse> page = ticketService.listWithinScope(
                user, scope, status,
                PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));

        return ToolResult.ok(ListFormat.render(page, tenantLabel, status), page);
    }

    private static String asString(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object v = args.get(key);
        return v == null ? null : v.toString();
    }
}
