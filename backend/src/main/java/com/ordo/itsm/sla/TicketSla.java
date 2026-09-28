package com.ordo.itsm.sla;

import com.ordo.itsm.ticket.Ticket;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ticket_slas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketSla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sla_policy_id", nullable = false)
    private SlaPolicy policy;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "response_due_at", nullable = false)
    private OffsetDateTime responseDueAt;

    @Column(name = "resolution_due_at", nullable = false)
    private OffsetDateTime resolutionDueAt;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "paused_at")
    private OffsetDateTime pausedAt;

    @Column(name = "paused_total_minutes", nullable = false)
    private int pausedTotalMinutes;

    @Column(name = "response_breached", nullable = false)
    private boolean responseBreached;

    @Column(name = "resolution_breached", nullable = false)
    private boolean resolutionBreached;

    public static TicketSla start(Ticket ticket, SlaPolicy policy, OffsetDateTime startedAt) {
        TicketSla sla = new TicketSla();
        sla.ticket = ticket;
        sla.policy = policy;
        sla.startedAt = startedAt;
        sla.responseDueAt = startedAt.plusMinutes(policy.getResponseMinutes());
        sla.resolutionDueAt = startedAt.plusMinutes(policy.getResolutionMinutes());
        return sla;
    }

    public boolean isPaused() {
        return pausedAt != null;
    }

    /** 고객 보완 대기(INFO_REQUIRED) 진입 시 정지. 이미 정지 중이면 무시 */
    public boolean pause(OffsetDateTime now) {
        if (isPaused() || resolvedAt != null) {
            return false;
        }
        this.pausedAt = now;
        return true;
    }

    /** 보완 완료 시 재개. 정지된 시간만큼 마감을 뒤로 민다 */
    public int resume(OffsetDateTime now) {
        if (!isPaused()) {
            return 0;
        }
        int minutes = (int) Math.max(0, Duration.between(pausedAt, now).toMinutes());
        this.pausedTotalMinutes += minutes;
        this.responseDueAt = responseDueAt.plusMinutes(minutes);
        this.resolutionDueAt = resolutionDueAt.plusMinutes(minutes);
        this.pausedAt = null;
        return minutes;
    }
}
