package com.ordo.itsm.notification;

import com.ordo.itsm.audit.AuditAction;
import com.ordo.itsm.audit.AuditLogService;
import com.ordo.itsm.global.exception.BusinessException;
import com.ordo.itsm.global.exception.ErrorCode;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.sla.SlaService;
import com.ordo.itsm.sla.TicketSla;
import com.ordo.itsm.tenant.Tenant;
import com.ordo.itsm.tenant.TenantRepository;
import com.ordo.itsm.ticket.EnvironmentType;
import com.ordo.itsm.ticket.RequestType;
import com.ordo.itsm.ticket.Ticket;
import com.ordo.itsm.ticket.TicketNumberGenerator;
import com.ordo.itsm.ticket.TicketRepository;
import com.ordo.itsm.user.User;
import com.ordo.itsm.user.UserRepository;
import com.ordo.itsm.user.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 시연용 장애 이벤트 생성 엔드포인트.
 * 실제 운영에선 AiWACS 이벤트 수신 지점 — 모니터링 시스템이 장애를 감지해 요청을 자동 생성하는 흐름을
 * 이 자리의 http 호출로 대신 모사한다.
 *
 * 설정 off: ordo.demo.endpoints-enabled=false 면 403.
 * 역할: OPS_ADMIN / OPERATOR 만 허용. 요청자(REQUESTER) 는 호출 불가.
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
public class IncidentDemoController {

    private final boolean demoEnabled;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final TicketNumberGenerator ticketNumberGenerator;
    private final SlaService slaService;
    private final AuditLogService auditLogService;
    private final SseNotificationService sseNotificationService;
    private final IncidentFirstResponsePolicy incidentPolicy;

    public IncidentDemoController(
            @Value("${ordo.demo.endpoints-enabled:true}") boolean demoEnabled,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            TicketRepository ticketRepository,
            TicketNumberGenerator ticketNumberGenerator,
            SlaService slaService,
            AuditLogService auditLogService,
            SseNotificationService sseNotificationService,
            IncidentFirstResponsePolicy incidentPolicy) {
        this.demoEnabled = demoEnabled;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.ticketNumberGenerator = ticketNumberGenerator;
        this.slaService = slaService;
        this.auditLogService = auditLogService;
        this.sseNotificationService = sseNotificationService;
        this.incidentPolicy = incidentPolicy;
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public IncidentDemoResponse createIncident(
            @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody IncidentDemoRequest request) {
        if (!demoEnabled) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "시연용 API 가 비활성화되어 있습니다.");
        }
        if (user.role() != UserRole.OPS_ADMIN && user.role() != UserRole.OPERATOR) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "장애 이벤트 수신은 운영 측 담당자만 호출할 수 있습니다.");
        }

        Tenant tenant = tenantRepository.findByCode(request.tenantCode().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "존재하지 않는 고객사 코드: " + request.tenantCode()));
        // 실제 운영에선 AiWACS 시스템 계정이 자동으로 요청자가 됨. 시연에선 해당 테넌트의 아무 요청자로 매핑.
        User requester = userRepository.findFirstByTenant_IdAndRole(tenant.getId(), UserRole.REQUESTER)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "해당 고객사에 요청자 계정이 없습니다: " + request.tenantCode()));

        String subject = "[장애] " + request.service().trim();
        String originalRequest = request.service().trim() + " 장애. " + request.summary().trim();
        Ticket ticket = Ticket.create(
                ticketNumberGenerator.next(), tenant, requester,
                subject, originalRequest);
        ticket.applyTriage(RequestType.INCIDENT, "SERVICE_OUTAGE",
                EnvironmentType.PRODUCTION, request.service().trim());
        ticket = ticketRepository.save(ticket);

        TicketSla sla = slaService.start(ticket);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("ticketNo", ticket.getTicketNo());
        after.put("tenantCode", tenant.getCode());
        after.put("service", request.service().trim());
        after.put("summary", request.summary().trim());
        after.put("source", "DEMO_INCIDENT");
        auditLogService.record(AuditAction.EVENT_RECEIVED, user, tenant.getId(), ticket.getId(), null, after);

        OffsetDateTime firstResponseDueAt = incidentPolicy.firstResponseDueAt(ticket.getCreatedAt());
        NotificationEvent event = new NotificationEvent(
                NotificationEvent.Type.INCIDENT_CREATED,
                ticket.getId(), ticket.getTicketNo(),
                tenant.getId(), tenant.getName(),
                requester.getId(),
                request.service().trim(),
                request.summary().trim(),
                firstResponseDueAt,
                OffsetDateTime.now()
        );
        sseNotificationService.broadcast(event);

        log.info("[DEMO] 장애 접수 ticket={} tenant={} service={}",
                ticket.getTicketNo(), tenant.getCode(), request.service());
        return new IncidentDemoResponse(ticket.getId(), ticket.getTicketNo(),
                firstResponseDueAt, sla.getResolutionDueAt());
    }

    public record IncidentDemoRequest(
            @NotBlank String tenantCode,
            @NotBlank String service,
            @NotBlank String summary
    ) {}

    public record IncidentDemoResponse(
            Long ticketId,
            String ticketNo,
            OffsetDateTime firstResponseDueAt,
            OffsetDateTime resolutionDueAt
    ) {}
}
