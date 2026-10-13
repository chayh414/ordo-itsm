package com.ordo.itsm.notification;

import com.ordo.itsm.audit.AuditAction;
import com.ordo.itsm.audit.AuditLogService;
import com.ordo.itsm.sla.TicketSla;
import com.ordo.itsm.sla.TicketSlaRepository;
import com.ordo.itsm.ticket.Ticket;
import com.ordo.itsm.ticket.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SLA 해결 기한이 임박한 요청을 주기적으로 찾아 SSE 로 SLA_NEAR 알림을 보낸다.
 *
 * - 스케줄: 30 초마다 (fixedDelay). initialDelay=15s 로 애플리케이션 시작 직후 폭주 방지.
 * - 조건: 정지(INFO_REQUIRED)·해결됨·종결 상태 제외, 해결 기한이 (now + threshold) 이내.
 * - 중복 억제: 티켓 id → 알린 시각 Map (JVM 메모리). 재시작 시 리셋. DB 테이블 미사용 (지시사항).
 * - 감사 로그: SLA_NEAR_NOTIFIED. actor=null 로 시스템 액션 표시 (AuditLog.actor_user_id nullable).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlaNearNotifier {

    /** SLA 가 더 이상 "임박" 알림 대상이 아닌 상태. DRAFT 는 아직 접수 전이라 안전차로 제외. */
    private static final Set<TicketStatus> TERMINAL = Set.of(
            TicketStatus.DRAFT,
            TicketStatus.RESOLVED, TicketStatus.COMPLETED, TicketStatus.VERIFIED,
            TicketStatus.CLOSED, TicketStatus.CANCELLED, TicketStatus.REJECTED);

    private final TicketSlaRepository ticketSlaRepository;
    private final SseNotificationService sseNotificationService;
    private final AuditLogService auditLogService;

    @Value("${ordo.sla.near-threshold-minutes:30}")
    private int thresholdMinutes;

    private final ConcurrentHashMap<Long, Instant> notified = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 30_000L, initialDelay = 15_000L)
    @Transactional(readOnly = true)
    public void checkNearDue() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime threshold = now.plusMinutes(thresholdMinutes);
        List<TicketSla> candidates = ticketSlaRepository.findNearDue(threshold, TERMINAL);
        for (TicketSla sla : candidates) {
            Ticket ticket = sla.getTicket();
            if (notified.putIfAbsent(ticket.getId(), Instant.now()) != null) {
                continue;    // 같은 요청은 한 번만
            }
            publish(sla, ticket, now);
        }
    }

    /** 재시작 없이 알림 기록을 초기화 — 테스트 전용 (프로덕션 사용 금지). */
    void resetNotifiedForTest() {
        notified.clear();
    }

    /** 트랜잭션 분리 — audit 는 REQUIRES_NEW 로 커밋되므로 readOnly 안에서도 안전. */
    private void publish(TicketSla sla, Ticket ticket, OffsetDateTime now) {
        NotificationEvent event = new NotificationEvent(
                NotificationEvent.Type.SLA_NEAR,
                ticket.getId(), ticket.getTicketNo(),
                ticket.getTenant().getId(), ticket.getTenant().getName(),
                ticket.getRequester().getId(),
                ticket.getSubject(),
                "마감까지 얼마 남지 않았어요",
                sla.getResolutionDueAt(),
                now
        );
        sseNotificationService.broadcast(event);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("ticketNo", ticket.getTicketNo());
        after.put("resolutionDueAt", sla.getResolutionDueAt().toString());
        after.put("thresholdMinutes", thresholdMinutes);
        auditLogService.recordIndependently(AuditAction.SLA_NEAR_NOTIFIED,
                null, ticket.getTenant().getId(), ticket.getId(), null, after);
        log.info("[SLA_NEAR] 알림 ticket={} dueAt={}", ticket.getTicketNo(), sla.getResolutionDueAt());
    }
}
