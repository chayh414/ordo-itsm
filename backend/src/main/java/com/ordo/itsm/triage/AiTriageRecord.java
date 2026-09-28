package com.ordo.itsm.triage;

import com.ordo.itsm.ticket.Ticket;
import com.ordo.itsm.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/** ai_triage_results 테이블. 재분석할 때마다 새 행(attempt_no 증가) */
@Entity
@Table(name = "ai_triage_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiTriageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 20)
    private TriageResultStatus resultStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json")
    private String resultJson;

    @Column(precision = 3, scale = 2)
    private BigDecimal confidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static AiTriageRecord of(Ticket ticket, int attemptNo, String modelName,
                                    TriageResultStatus status, String resultJson, Double confidence) {
        AiTriageRecord r = new AiTriageRecord();
        r.ticket = ticket;
        r.attemptNo = attemptNo;
        r.modelName = modelName;
        r.resultStatus = status;
        r.resultJson = resultJson;
        r.confidence = confidence == null ? null : BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP);
        r.createdAt = OffsetDateTime.now();
        return r;
    }
}
