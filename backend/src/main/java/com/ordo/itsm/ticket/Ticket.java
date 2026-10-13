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
        Ticket ticket = base(ticketNo, tenant, requester, subject, originalRequest);
        ticket.status = TicketStatus.SUBMITTED;
        return ticket;
    }

    /**
     * 어시스턴트가 LLM 판단만으로 임시 생성하는 초안.
     * 사용자가 카드에서 [접수하기] 를 눌러 submit() 할 때 SUBMITTED 로 전환되며 SLA 가 시작된다.
     */
    public static Ticket createDraft(String ticketNo, Tenant tenant, User requester,
                                     String subject, String originalRequest) {
        Ticket ticket = base(ticketNo, tenant, requester, subject, originalRequest);
        ticket.status = TicketStatus.DRAFT;
        return ticket;
    }

    private static Ticket base(String ticketNo, Tenant tenant, User requester,
                               String subject, String originalRequest) {
        Ticket ticket = new Ticket();
        ticket.ticketNo = ticketNo;
        ticket.tenant = tenant;
        ticket.requester = requester;
        ticket.subject = subject;
        ticket.originalRequest = originalRequest;
        ticket.detailJson = "{}";
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

    /** RiskPolicyService 가 산정한 등급을 티켓에 반영. */
    public void applyRisk(RiskGrade grade) {
        this.riskGrade = grade;
    }

    /** detail_json 전체 교체. 호출자가 직렬화 책임 (TicketService 쪽에서 JsonMapper 사용). */
    public void applyDetailJson(String json) {
        this.detailJson = json == null ? "{}" : json;
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
