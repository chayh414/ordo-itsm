package com.ordo.itsm.risk;

import com.ordo.itsm.ticket.RiskGrade;
import com.ordo.itsm.ticket.Ticket;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 위험도 산정 결과 1회분. 재분석할 때마다 새 행이 쌓이고, 가장 최근 1건만 is_current=true.
 * factors_json 은 {"production_environment":3, "night_work":1, ...} 형태로 요인별 점수 저장.
 */
@Entity
@Table(name = "risk_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskGrade grade;

    /** 요인별 점수 분해 (화면에 요인 오른쪽 "+3" 로 표시). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "factors_json", nullable = false)
    private String factorsJson;

    @Column(name = "policy_version", nullable = false, length = 20)
    private String policyVersion;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static RiskAssessment create(Ticket ticket, int score, RiskGrade grade,
                                         String factorsJson, String policyVersion) {
        RiskAssessment a = new RiskAssessment();
        a.ticket = ticket;
        a.score = score;
        a.grade = grade;
        a.factorsJson = factorsJson;
        a.policyVersion = policyVersion;
        a.current = true;
        a.createdAt = OffsetDateTime.now();
        return a;
    }

    /** 새 평가가 들어오면 이전 current 행에 호출해 플래그를 내린다. */
    public void markSuperseded() {
        this.current = false;
    }
}
