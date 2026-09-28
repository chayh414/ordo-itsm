package com.ordo.itsm.ticket;

import com.ordo.itsm.audit.AuditAction;
import com.ordo.itsm.audit.AuditLogService;
import com.ordo.itsm.global.common.PageResponse;
import com.ordo.itsm.global.exception.BusinessException;
import com.ordo.itsm.global.exception.ErrorCode;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.sla.SlaResponse;
import com.ordo.itsm.sla.SlaService;
import com.ordo.itsm.sla.TicketSla;
import com.ordo.itsm.sla.TicketSlaRepository;
import com.ordo.itsm.tenant.Tenant;
import com.ordo.itsm.tenant.TenantAccessService;
import com.ordo.itsm.tenant.TenantRepository;
import com.ordo.itsm.tenant.TenantScope;
import com.ordo.itsm.ticket.dto.CreateTicketRequest;
import com.ordo.itsm.ticket.dto.TicketCreatedResponse;
import com.ordo.itsm.ticket.dto.TicketDetailResponse;
import com.ordo.itsm.ticket.dto.TicketSummaryResponse;
import com.ordo.itsm.user.User;
import com.ordo.itsm.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TicketNumberGenerator ticketNumberGenerator;
    private final TenantAccessService tenantAccessService;
    private final SlaService slaService;
    private final TicketSlaRepository ticketSlaRepository;
    private final AuditLogService auditLogService;
    private final JsonMapper jsonMapper;

    /** 요청 등록: 원문 저장 → 티켓번호 발급 → SLA 시작 → 감사 로그 */
    @Transactional
    public TicketCreatedResponse create(AuthUser user, CreateTicketRequest request) {
        if (user.isProviderSide()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "요청 등록은 고객사 사용자만 할 수 있습니다.");
        }

        // tenantId는 클라이언트 입력이 아니라 토큰에서 꺼낸 값을 사용
        Tenant tenant = tenantRepository.getReferenceById(user.tenantId());
        User requester = userRepository.getReferenceById(user.userId());

        Ticket ticket = ticketRepository.save(Ticket.create(
                ticketNumberGenerator.next(),
                tenant,
                requester,
                request.subject().trim(),
                request.originalRequest().trim()
        ));

        TicketSla sla = slaService.start(ticket);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("ticketNo", ticket.getTicketNo());
        after.put("subject", ticket.getSubject());
        after.put("status", ticket.getStatus().name());
        after.put("slaResolutionDueAt", sla.getResolutionDueAt().toString());
        auditLogService.record(AuditAction.TICKET_CREATED, user, user.tenantId(), ticket.getId(), null, after);

        return new TicketCreatedResponse(
                ticket.getId(),
                ticket.getTicketNo(),
                ticket.getStatus(),
                SlaResponse.from(sla),
                null,
                ticket.getCreatedAt()
        );
    }

    /** 목록: 로그인 사용자의 고객사 접근 범위 안에서만 조회 */
    public PageResponse<TicketSummaryResponse> list(AuthUser user, TicketStatus status, Pageable pageable) {
        TenantScope scope = tenantAccessService.scopeOf(user);
        if (scope.isEmpty()) {
            return PageResponse.empty(pageable);
        }

        Page<Ticket> page;
        if (scope.all()) {
            page = (status == null)
                    ? ticketRepository.findAll(pageable)
                    : ticketRepository.findByStatus(status, pageable);
        } else {
            page = (status == null)
                    ? ticketRepository.findByTenant_IdIn(scope.tenantIds(), pageable)
                    : ticketRepository.findByTenant_IdInAndStatus(scope.tenantIds(), status, pageable);
        }

        List<Long> ids = page.getContent().stream().map(Ticket::getId).toList();
        Map<Long, TicketSla> slaByTicketId = ids.isEmpty()
                ? Map.of()
                : ticketSlaRepository.findByTicket_IdIn(ids).stream()
                        .collect(Collectors.toMap(s -> s.getTicket().getId(), Function.identity()));

        return PageResponse.from(page.map(t -> TicketSummaryResponse.from(t, slaByTicketId.get(t.getId()))));
    }

    /** 상세: 다른 고객사 티켓이면 403 + 접근 거부 감사 로그 */
    public TicketDetailResponse get(AuthUser user, Long ticketId) {
        Ticket ticket = ticketRepository.findWithTenantAndRequesterById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));

        Long ticketTenantId = ticket.getTenant().getId();
        if (!tenantAccessService.scopeOf(user).includes(ticketTenantId)) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("reason", "TENANT_MISMATCH");
            detail.put("targetTicketNo", ticket.getTicketNo());
            detail.put("targetTenantId", ticketTenantId);
            auditLogService.recordIndependently(AuditAction.ACCESS_DENIED, user, user.tenantId(), ticket.getId(), null, detail);

            throw new BusinessException(ErrorCode.ACCESS_DENIED, "다른 고객사의 티켓에는 접근할 수 없습니다.");
        }

        TicketSla sla = ticketSlaRepository.findByTicket_Id(ticketId).orElse(null);
        return TicketDetailResponse.from(ticket, sla, parseDetails(ticket.getDetailJson()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDetails(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return jsonMapper.readValue(json, Map.class);
    }
}
