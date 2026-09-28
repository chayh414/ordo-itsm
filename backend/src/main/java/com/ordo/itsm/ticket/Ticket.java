package com.ordo.itsm.ticket;

import com.ordo.itsm.tenant.Tenant;
import com.ordo.itsm.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tickets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_no", nullable = false, unique = true, length = 30)
    private String ticketNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", length = 30)
    private RequestType requestType;

    @Column(length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_grade", length = 10)
    private RiskGrade riskGrade;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(name = "original_request", nullable = false, columnDefinition = "text")
    private String originalRequest;

    /** 유형별 추출 항목 (IP, 포트, 롤백 계획 등) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", nullable = false)
    private String detailJson;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EnvironmentType environment;

    @Column(name = "affected_service", length = 100)
    private String affectedService;

    @Column(name = "assigned_team", length = 50)
    private String assignedTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Ticket create(String ticketNo, Tenant tenant, User requester,
                                String subject, String originalRequest) {
        Ticket ticket = new Ticket();
        ticket.ticketNo = ticketNo;
        ticket.tenant = tenant;
        ticket.requester = requester;
        ticket.subject = subject;
        ticket.originalRequest = originalRequest;
        ticket.detailJson = "{}";
        ticket.status = TicketStatus.SUBMITTED;
        return ticket;
    }

    /**
     * AI 트리아지 결과를 "추천값"으로 반영한다.
     * 우선순위는 운영 담당자가 확정하므로 여기서 설정하지 않는다.
     */
    public void applyTriage(RequestType requestType, String category,
                            EnvironmentType environment, String affectedService) {
        this.requestType = requestType;
        this.category = category;
        if (environment != null) this.environment = environment;
        if (affectedService != null) this.affectedService = affectedService;
    }

    public void changeStatus(TicketStatus next) {
        this.status = next;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
