package com.ordo.itsm.triage;

import com.ordo.itsm.ticket.RequestType;
import com.ordo.itsm.triage.TriageResult.Evidence;
import com.ordo.itsm.triage.engine.TriageEngineException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 결과를 그대로 믿지 않고 서버가 한 번 더 검증·보정한다 (요청 처리 ④단계).
 * - 허용 목록 밖의 값 제거
 * - 롤백·테스트 누락은 AI가 빠뜨려도 규칙으로 보완
 * - 확신도 범위 보정, 보완 질문 생성
 */
@Component
public class TriageValidator {

    public TriageResult validate(TriageResult r) {
        if (r == null || r.requestType() == null) {
            throw new TriageEngineException(TriageEngineException.Kind.INVALID_JSON,
                    "requestType이 없습니다.", null, null);
        }

        String category = TriageVocabulary.CATEGORIES.contains(r.category()) ? r.category() : "OTHER";
        String team = TriageVocabulary.TEAMS.contains(r.recommendedTeam()) ? r.recommendedTeam() : "SERVICE_DESK";

        Set<String> missing = new LinkedHashSet<>(filter(r.missingFields(), TriageVocabulary.FIELD_QUESTIONS.keySet()));
        Set<String> factors = new LinkedHashSet<>(filter(r.riskFactors(), Set.copyOf(TriageVocabulary.RISK_FACTORS)));

        // 변경 요청의 롤백·테스트 누락은 결정적 규칙으로 한 번 더 보장
        if (r.requestType() == RequestType.CHANGE) {
            if (Boolean.FALSE.equals(r.rollbackPlanProvided())) {
                missing.add("rollbackPlan");
                factors.add("rollback_plan_missing");
            }
            if (Boolean.FALSE.equals(r.testPlanProvided())) {
                missing.add("testPlan");
                factors.add("test_plan_missing");
            }
        } else {
            // 변경 요청이 아니면 변경 전용 위험 요인은 의미가 없음
            factors.remove("rollback_plan_missing");
            factors.remove("test_plan_missing");
        }

        List<Evidence> evidence = r.evidence() == null ? List.of()
                : r.evidence().stream()
                .filter(e -> e != null && factors.contains(e.factor()))
                .toList();

        List<String> questions = new ArrayList<>();
        for (String field : missing) {
            questions.add(TriageVocabulary.FIELD_QUESTIONS.get(field));
        }

        double confidence = r.confidence() == null ? 0.0 : Math.max(0.0, Math.min(1.0, r.confidence()));

        return new TriageResult(
                r.requestType(), category, r.environment(), r.requestedTime(),
                r.recommendedPriority(), team, blankToNull(r.affectedService()),
                r.testPlanProvided(), r.rollbackPlanProvided(),
                List.copyOf(missing), List.copyOf(factors), evidence,
                questions, confidence
        );
    }

    private static List<String> filter(List<String> values, Set<String> allowed) {
        if (values == null) return List.of();
        return values.stream().filter(allowed::contains).toList();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
