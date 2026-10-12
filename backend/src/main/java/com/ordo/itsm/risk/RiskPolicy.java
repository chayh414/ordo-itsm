package com.ordo.itsm.risk;

import com.ordo.itsm.ticket.RiskGrade;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 위험도 계산 규칙 v1 — 순수 로직. Spring/DB 의존 없이 단독 테스트 가능.
 *
 * 입력은 TriageValidator 가 검증·보정한 최종 riskFactors (AI 원본 아님). 모르는 요인은 0점으로 간주.
 *
 * 점수:
 *   production_environment       +3
 *   database_schema_change       +3
 *   database_permission_change   +3
 *   firewall_change              +3
 *   network_change               +3
 *   rollback_plan_missing        +2
 *   test_plan_missing            +2
 *   night_work                   +1
 *
 * 등급:
 *   0~2 LOW · 3~5 MEDIUM · 6~8 HIGH · 9+ CRITICAL
 */
public final class RiskPolicy {

    public static final String VERSION = "v1";

    private static final Map<String, Integer> POINTS = Map.of(
            "production_environment", 3,
            "database_schema_change", 3,
            "database_permission_change", 3,
            "firewall_change", 3,
            "network_change", 3,
            "rollback_plan_missing", 2,
            "test_plan_missing", 2,
            "night_work", 1
    );

    private RiskPolicy() {
    }

    public static Outcome score(List<String> factors) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        int total = 0;
        if (factors != null) {
            for (String f : factors) {
                Integer p = POINTS.get(f);
                if (p == null) continue;         // vocab 밖 요인은 0점 (안전쪽 — 임의 가산 금지)
                if (breakdown.containsKey(f)) continue;   // 중복 요인 무시
                breakdown.put(f, p);
                total += p;
            }
        }
        return new Outcome(total, gradeFor(total), breakdown);
    }

    public static RiskGrade gradeFor(int score) {
        if (score >= 9) return RiskGrade.CRITICAL;
        if (score >= 6) return RiskGrade.HIGH;
        if (score >= 3) return RiskGrade.MEDIUM;
        return RiskGrade.LOW;
    }

    /** 요인별 점수 분해 + 총점 + 등급. factorPoints 는 입력 순서를 보존한다 (화면 정렬용). */
    public record Outcome(int score, RiskGrade grade, Map<String, Integer> factorPoints) {
    }
}
