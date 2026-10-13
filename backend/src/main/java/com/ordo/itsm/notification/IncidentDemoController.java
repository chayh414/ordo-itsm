package com.ordo.itsm.notification;

import com.ordo.itsm.audit.AuditAction;
import com.ordo.itsm.audit.AuditLogService;
import com.ordo.itsm.global.exception.BusinessException;
import com.ordo.itsm.global.exception.ErrorCode;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.sla.SlaPolicy;
import com.ordo.itsm.sla.SlaPolicyRepository;
import com.ordo.itsm.sla.TicketSla;
import com.ordo.itsm.sla.TicketSlaRepository;
import com.ordo.itsm.ticket.Priority;
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
import org.springframework.web.bind.annotation.PathVariable;
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
    private final TicketSlaRepository ticketSlaRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final TicketNumberGenerator ticketNumberGenerator;
    private final AuditLogService auditLogService;
    private final SseNotificationService sseNotificationService;
    private final IncidentFirstResponsePolicy incidentPolicy;

    public IncidentDemoController(
            @Value("${ordo.demo.endpoints-enabled:true}") boolean demoEnabled,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            TicketRepository ticketRepository,
            TicketSlaRepository ticketSlaRepository,
            SlaPolicyRepository slaPolicyRepository,
            TicketNumberGenerator ticketNumberGenerator,
            AuditLogService auditLogService,
            SseNotificationService sseNotificationService,
            IncidentFirstResponsePolicy incidentPolicy) {
        this.demoEnabled = demoEnabled;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.ticketSlaRepository = ticketSlaRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.ticketNumberGenerator = ticketNumberGenerator;
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
        // 실제 운영에선 AiWACS 이벤트의 시스템 계정이 자동으로 requester 로 들어온다.
        // 시연에선 테넌트별 monitoring@{code}.demo (DemoDataInitializer 가 미리 생성, 로그인 불가) 를 사용.
        String code = tenant.getCode().toLowerCase().replace("-", "_");
        User requester = userRepository.findByEmail("monitoring@" + code + ".demo")
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "모니터링 시스템 계정이 없는 테넌트: " + request.tenantCode()));

        // 실제 운영에선 AiWACS 이벤트가 심각도를 들고 오는 것과 같은 구조.
        // severity 가 비면 P2 (응답 30분·해결 8시간) 를 기본으로 적용.
        Priority severity = request.severity() != null ? request.severity() : Priority.P2;
        SlaPolicy policy = slaPolicyRepository.findByTenant_IdAndPriority(tenant.getId(), severity)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "해당 테넌트에 " + severity + " SLA 정책이 없습니다."));

        String subject = "[장애] " + request.service().trim();
        String originalRequest = request.service().trim() + " 장애. " + request.summary().trim();
        Ticket ticket = Ticket.create(
                ticketNumberGenerator.next(), tenant, requester,
                subject, originalRequest);
        ticket.applyTriage(RequestType.INCIDENT, "SERVICE_OUTAGE",
                EnvironmentType.PRODUCTION, request.service().trim());
        ticket = ticketRepository.save(ticket);
        // SlaService.start 는 provisional=P3 로 고정이라 severity 반영을 위해 여기서 직접 생성.
        TicketSla sla = ticketSlaRepository.save(
                TicketSla.start(ticket, policy, ticket.getCreatedAt()));

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("ticketNo", ticket.getTicketNo());
        after.put("tenantCode", tenant.getCode());
        after.put("service", request.service().trim());
        after.put("summary", request.summary().trim());
        after.put("severity", severity.name());
        after.put("source", "DEMO_INCIDENT");
        auditLogService.record(AuditAction.EVENT_RECEIVED, user, tenant.getId(), ticket.getId(), null, after);

        OffsetDateTime firstResponseDueAt = incidentPolicy.firstResponseDueAt(sla);
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

    /**
     * 시연용 — 지정 티켓의 해결 기한을 "지금 + 10분" 으로 당긴다.
     * 다음 SlaNearNotifier 스케줄(30초 이내) 에서 SLA_NEAR 알림이 발송된다.
     */
    @PostMapping("/tickets/{ticketId}/sla-near")
    @Transactional
    public SlaAdjustResponse adjustSlaNear(
            @AuthenticationPrincipal AuthUser user,
            @PathVariable Long ticketId) {
        if (!demoEnabled) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "시연용 API 가 비활성화되어 있습니다.");
        }
        if (user.role() != UserRole.OPS_ADMIN) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "SLA 마감 조정은 관리자만 호출할 수 있습니다.");
        }

        TicketSla sla = ticketSlaRepository.findByTicket_Id(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND,
                        "해당 티켓의 SLA 가 없습니다: " + ticketId));

        OffsetDateTime oldDue = sla.getResolutionDueAt();
        OffsetDateTime newDue = OffsetDateTime.now().plusMinutes(10);
        sla.pullResolutionDueTo(newDue);

        Map<String, Object> before = Map.of("resolutionDueAt", oldDue.toString());
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("resolutionDueAt", newDue.toString());
        after.put("source", "DEMO_SLA_NEAR");
        auditLogService.record(AuditAction.DEMO_SLA_ADJUSTED, user,
                sla.getTicket().getTenant().getId(), ticketId, before, after);

        log.info("[DEMO] SLA 당김 ticket={} {} → {}",
                sla.getTicket().getTicketNo(), oldDue, newDue);
        return new SlaAdjustResponse(ticketId, sla.getTicket().getTicketNo(), newDue);
    }

    public record IncidentDemoRequest(
            @NotBlank String tenantCode,
            @NotBlank String service,
            @NotBlank String summary,
            /** 실제 운영에선 AiWACS 이벤트가 심각도를 들고 오는 것과 같은 구조. 미지정 시 P2. */
            Priority severity
    ) {}

    public record IncidentDemoResponse(
            Long ticketId,
            String ticketNo,
            OffsetDateTime firstResponseDueAt,
            OffsetDateTime resolutionDueAt
    ) {}

    public record SlaAdjustResponse(
            Long ticketId,
            String ticketNo,
            OffsetDateTime resolutionDueAt
    ) {}
}
