package com.ordo.itsm.notification;

import com.ordo.itsm.audit.AuditAction;
import com.ordo.itsm.audit.AuditLogService;
import com.ordo.itsm.sla.TicketSla;
import com.ordo.itsm.sla.TicketSlaRepository;
import com.ordo.itsm.tenant.Tenant;
import com.ordo.itsm.ticket.Ticket;
import com.ordo.itsm.ticket.TicketStatus;
import com.ordo.itsm.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SlaNearNotifier 테스트.
 * - 메모리 중복 억제 (같은 ticket 두 번 호출돼도 알림 1번만)
 * - 범위 필터는 SseNotificationServiceTest 가 커버 — 여기선 broadcast 가 호출됐는지와 payload 만 확인
 */
class SlaNearNotifierTest {

    private TicketSlaRepository slaRepo;
    private SseNotificationService sse;
    private AuditLogService audit;
    private SlaNearNotifier notifier;

    @BeforeEach
    void setUp() {
        slaRepo = mock(TicketSlaRepository.class);
        sse = mock(SseNotificationService.class);
        audit = mock(AuditLogService.class);
        notifier = new SlaNearNotifier(slaRepo, sse, audit);
        ReflectionTestUtils.setField(notifier, "thresholdMinutes", 30);
    }

    @Test
    @DisplayName("임박 후보가 있으면 SLA_NEAR broadcast + SLA_NEAR_NOTIFIED 감사")
    void publishesAndAudits_whenCandidateFound() {
        TicketSla sla = fakeSla(42L, "ORD-x", 100L, "A대학교", 7L, "운영 DB 컬럼 추가",
                OffsetDateTime.now().plusMinutes(15));
        when(slaRepo.findNearDue(any(), any())).thenReturn(List.of(sla));

        notifier.checkNearDue();

        ArgumentCaptor<NotificationEvent> cap = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(sse).broadcast(cap.capture());
        NotificationEvent ev = cap.getValue();
        assertEquals(NotificationEvent.Type.SLA_NEAR, ev.type());
        assertEquals(42L, ev.ticketId());
        assertEquals("ORD-x", ev.ticketNo());
        assertEquals(7L, ev.requesterId(), "요청자 필터링을 위해 requesterId 가 포함되어야 함");
        assertNotNull(ev.dueAt());

        verify(audit).recordIndependently(eq(AuditAction.SLA_NEAR_NOTIFIED),
                isNull(), eq(100L), eq(42L), isNull(), any());
    }

    @Test
    @DisplayName("같은 티켓은 중복 알림 금지 — 2회 스케줄에도 broadcast 1회만")
    void dedupe_sameTicketOnlyOnce() {
        TicketSla sla = fakeSla(42L, "ORD-x", 100L, "A대학교", 7L, "subject",
                OffsetDateTime.now().plusMinutes(15));
        when(slaRepo.findNearDue(any(), any())).thenReturn(List.of(sla));

        notifier.checkNearDue();
        notifier.checkNearDue();

        verify(sse, times(1)).broadcast(any(NotificationEvent.class));
    }

    @Test
    @DisplayName("terminal 상태는 쿼리 파라미터로 제외 (DRAFT, RESOLVED, COMPLETED, VERIFIED, CLOSED, CANCELLED, REJECTED)")
    void queryExcludesTerminalStates() {
        when(slaRepo.findNearDue(any(), any())).thenReturn(List.of());

        notifier.checkNearDue();

        ArgumentCaptor<Collection<TicketStatus>> cap = ArgumentCaptor.forClass(Collection.class);
        verify(slaRepo).findNearDue(any(), cap.capture());
        Collection<TicketStatus> terminal = cap.getValue();
        assertEquals(7, terminal.size(), "7개 종결 상태가 쿼리에서 제외되어야 함");
        for (TicketStatus s : List.of(
                TicketStatus.DRAFT, TicketStatus.RESOLVED, TicketStatus.COMPLETED,
                TicketStatus.VERIFIED, TicketStatus.CLOSED, TicketStatus.CANCELLED,
                TicketStatus.REJECTED)) {
            org.junit.jupiter.api.Assertions.assertTrue(terminal.contains(s),
                    s + " 가 terminal 집합에 있어야 함");
        }
    }

    @Test
    @DisplayName("resetNotifiedForTest 후에는 같은 티켓이라도 다시 알림")
    void reset_allowsRenotify() {
        TicketSla sla = fakeSla(42L, "ORD-x", 100L, "A대학교", 7L, "subject",
                OffsetDateTime.now().plusMinutes(15));
        when(slaRepo.findNearDue(any(), any())).thenReturn(List.of(sla));

        notifier.checkNearDue();
        notifier.resetNotifiedForTest();
        notifier.checkNearDue();

        verify(sse, times(2)).broadcast(any(NotificationEvent.class));
    }

    private TicketSla fakeSla(Long ticketId, String ticketNo, Long tenantId, String tenantName,
                              Long requesterId, String subject, OffsetDateTime dueAt) {
        TicketSla sla = mock(TicketSla.class);
        Ticket t = mock(Ticket.class);
        Tenant tenant = mock(Tenant.class);
        User requester = mock(User.class);
        when(sla.getTicket()).thenReturn(t);
        when(sla.getResolutionDueAt()).thenReturn(dueAt);
        when(t.getId()).thenReturn(ticketId);
        when(t.getTicketNo()).thenReturn(ticketNo);
        when(t.getSubject()).thenReturn(subject);
        when(t.getTenant()).thenReturn(tenant);
        when(t.getRequester()).thenReturn(requester);
        when(tenant.getId()).thenReturn(tenantId);
        when(tenant.getName()).thenReturn(tenantName);
        when(requester.getId()).thenReturn(requesterId);
        return sla;
    }
}
