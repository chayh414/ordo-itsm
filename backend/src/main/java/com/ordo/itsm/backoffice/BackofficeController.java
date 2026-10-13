package com.ordo.itsm.backoffice;

import com.ordo.itsm.audit.AuditAction;
import com.ordo.itsm.audit.AuditLog;
import com.ordo.itsm.audit.AuditLogRepository;
import com.ordo.itsm.audit.AuditLogService;
import com.ordo.itsm.global.exception.BusinessException;
import com.ordo.itsm.global.exception.ErrorCode;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.risk.RiskAssessment;
import com.ordo.itsm.risk.RiskAssessmentRepository;
import com.ordo.itsm.tenant.TenantAccessService;
import com.ordo.itsm.ticket.RiskGrade;
import com.ordo.itsm.ticket.Ticket;
import com.ordo.itsm.ticket.TicketRepository;
import com.ordo.itsm.user.User;
import com.ordo.itsm.user.UserRepository;
import com.ordo.itsm.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 백오피스 전용 읽기 API.
 * - 운영 측(OPS_ADMIN / OPERATOR / APPROVER) 만 접근 가능.
 * - 테넌트 접근 범위 안의 티켓만 조회 허용. 범위 밖이면 ACCESS_DENIED 감사 로그 + 403.
 * - 승인·상태 변경 같은 쓰기 기능은 없음 (시연용 화면이라는 전제).
 */
@RestController
@RequestMapping("/api/backoffice")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BackofficeController {

    private static final Set<UserRole> PROVIDER_ROLES = Set.of(
            UserRole.OPS_ADMIN, UserRole.OPERATOR, UserRole.APPROVER);

    private final TicketRepository ticketRepository;
    private final TenantAccessService tenantAccessService;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final JsonMapper jsonMapper;

    @GetMapping("/tickets/{ticketId}/risk-history")
    public List<RiskHistoryEntry> riskHistory(
            @AuthenticationPrincipal AuthUser user,
            @PathVariable Long ticketId) {
        Ticket ticket = loadAccessible(user, ticketId, "RISK_HISTORY");
        return riskAssessmentRepository
                .findByTicket_IdOrderByCreatedAtDesc(ticket.getId())
                .stream()
                .map(this::toRiskEntry)
                .toList();
    }

    @GetMapping("/tickets/{ticketId}/audit")
    public List<AuditEntry> audit(
            @AuthenticationPrincipal AuthUser user,
            @PathVariable Long ticketId) {
        Ticket ticket = loadAccessible(user, ticketId, "AUDIT");
        List<AuditLog> logs = auditLogRepository.findByTicketIdOrderByCreatedAtDesc(ticket.getId());

        // actor 이름을 한번에 조회 — 로그가 많아도 N+1 방지.
        Set<Long> actorIds = logs.stream()
                .map(AuditLog::getActorId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<Long, String> actorNames = userRepository.findAllById(actorIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getName));

        return logs.stream()
                .map(l -> new AuditEntry(
                        l.getId(),
                        l.getAction(),
                        l.getActorId(),
                        l.getActorId() == null ? "시스템" : actorNames.getOrDefault(l.getActorId(), "삭제된 사용자"),
                        parse(l.getBeforeJson()),
                        parse(l.getAfterJson()),
                        l.getCreatedAt()))
                .toList();
    }

    private Ticket loadAccessible(AuthUser user, Long ticketId, String targetTag) {
        if (!PROVIDER_ROLES.contains(user.role())) {
            // 운영측이 아닌 사용자(요청자 등) 접근은 거부 + 감사
            auditLogService.recordIndependently(AuditAction.ACCESS_DENIED, user, user.tenantId(), ticketId, null,
                    Map.of("reason", "NOT_PROVIDER_SIDE", "target", "BACKOFFICE_" + targetTag));
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "백오피스는 운영 측 담당자만 접근할 수 있습니다.");
        }
        Ticket ticket = ticketRepository.findWithTenantAndRequesterById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
        Long ticketTenantId = ticket.getTenant().getId();
        if (!tenantAccessService.scopeOf(user).includes(ticketTenantId)) {
            auditLogService.recordIndependently(AuditAction.ACCESS_DENIED, user, user.tenantId(), ticketId, null,
                    Map.of("reason", "TENANT_MISMATCH", "target", "BACKOFFICE_" + targetTag,
                            "targetTicketNo", ticket.getTicketNo()));
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "다른 고객사의 티켓에는 접근할 수 없습니다.");
        }
        return ticket;
    }

    private RiskHistoryEntry toRiskEntry(RiskAssessment a) {
        return new RiskHistoryEntry(
                a.getId(), a.getScore(), a.getGrade(),
                parseFactors(a.getFactorsJson()),
                a.isCurrent(),
                a.getCreatedAt());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return jsonMapper.readValue(json, Map.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> parseFactors(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> raw = jsonMapper.readValue(json, Map.class);
            Map<String, Integer> out = new LinkedHashMap<>();
            for (var e : raw.entrySet()) {
                if (e.getValue() instanceof Number n) out.put(e.getKey(), n.intValue());
            }
            return out;
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    public record RiskHistoryEntry(
            Long id,
            int score,
            RiskGrade grade,
            Map<String, Integer> factorPoints,
            boolean current,
            OffsetDateTime createdAt
    ) {}

    public record AuditEntry(
            Long id,
            String action,
            Long actorId,
            String actorName,
            Map<String, Object> before,
            Map<String, Object> after,
            OffsetDateTime createdAt
    ) {}
}
