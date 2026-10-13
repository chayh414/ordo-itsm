package com.ordo.itsm.triage;

import com.ordo.itsm.ticket.EnvironmentType;
import com.ordo.itsm.ticket.Priority;
import com.ordo.itsm.ticket.RequestType;
import com.ordo.itsm.triage.TriageResult.Evidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 고객 보완(supplements) 반영 시 TriageValidator 가 원문 부정보다 supplements 를 우선해
 * missing·factor 를 제거하는지 검증.
 */
class TriageValidatorSupplementTest {

    private final TriageValidator validator = new TriageValidator();

    @Test
    @DisplayName("롤백 보완 — 원문에 '롤백 아직 없어요' 가 남아 있어도 롤백 요인·누락 제거, [SUP] 로그")
    void rollbackSupplement_overridesTextNegation() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "23:00", Priority.P2, "DB_OPERATION",
                "학생 포털 테이블", true, false,
                List.of("rollbackPlan"),
                List.of("database_schema_change", "production_environment",
                        "rollback_plan_missing", "night_work"),
                List.of(
                        new Evidence("database_schema_change", "컬럼 추가"),
                        new Evidence("rollback_plan_missing", "롤백 절차는 아직 없어요")
                ),
                0.85);
        String raw = "오늘 23시에 운영 DB 학생 포털 테이블에 컬럼 추가하려고요. 테스트는 했는데 롤백 절차는 아직 없어요.";
        Map<String, String> supplements = Map.of(
                "rollbackPlan", "작업 전 테이블 백업 + 추가 컬럼 삭제 스크립트 준비");

        TriageResult out = validator.validate(ai, raw, supplements);

        assertFalse(out.missingFields().contains("rollbackPlan"), "rollbackPlan 보완 → missing 에서 제거");
        assertFalse(out.riskFactors().contains("rollback_plan_missing"), "rollback_plan_missing 위험 요인 제거");
        assertTrue(out.corrections().stream().anyMatch(c -> c.startsWith("[SUP] rollbackPlan")),
                "[SUP] 보정 로그 기록");
        assertTrue(out.corrections().stream().noneMatch(c -> c.startsWith("[FIX]")),
                "[SUP] 는 [FIX] 가 아니므로 확신도 하향 트리거 없음");
        assertEquals(0.85, out.confidence(), 1e-9, "확신도 유지");
    }

    @Test
    @DisplayName("테스트 보완 — testPlan 보완으로 test_plan_missing 함께 해소")
    void testPlanSupplement_removesFactor() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "14:00", Priority.P2, "DB_OPERATION",
                "users 테이블", false, true,
                List.of("testPlan"),
                List.of("database_schema_change", "production_environment", "test_plan_missing"),
                List.of(),
                0.8);
        String raw = "운영 DB users 테이블 변경. 테스트는 아직 못 했음.";
        Map<String, String> supplements = Map.of(
                "testPlan", "스테이징에서 동일 SQL 검증 완료, 영향 사용자 수 집계함");

        TriageResult out = validator.validate(ai, raw, supplements);

        assertFalse(out.missingFields().contains("testPlan"));
        assertFalse(out.riskFactors().contains("test_plan_missing"));
        assertTrue(out.corrections().stream().anyMatch(c -> c.contains("[SUP] testPlan")));
    }

    @Test
    @DisplayName("일반 필드 보완 — scheduledTime 보완 시 missing 에서만 제거 (대응 factor 없음)")
    void scheduledTimeSupplement_onlyRemovesMissing() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "FIREWALL", EnvironmentType.PRODUCTION,
                null, Priority.P3, "NETWORK_SECURITY",
                null, null, null,
                List.of("sourceIp", "destinationIp", "port", "scheduledTime"),
                List.of("firewall_change", "production_environment"),
                List.of(), 0.7);
        String raw = "운영 방화벽 열어주세요.";
        Map<String, String> supplements = Map.of("scheduledTime", "오늘 23시");

        TriageResult out = validator.validate(ai, raw, supplements);

        assertFalse(out.missingFields().contains("scheduledTime"), "scheduledTime missing 제거");
        assertTrue(out.riskFactors().contains("firewall_change"), "다른 factor 는 유지");
        assertTrue(out.corrections().stream().anyMatch(c -> c.contains("[SUP] scheduledTime")));
    }

    @Test
    @DisplayName("보완 안 된 필드는 그대로 missing 유지")
    void unsuppliedField_staysMissing() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "23:00", Priority.P2, "DB_OPERATION",
                "users", true, false,
                List.of("rollbackPlan"),
                List.of("database_schema_change", "rollback_plan_missing"),
                List.of(new Evidence("rollback_plan_missing", "롤백 없음")),
                0.8);
        String raw = "운영 users 테이블 컬럼 추가. 롤백 없음.";
        Map<String, String> supplements = Map.of();  // 아무 보완 없음

        TriageResult out = validator.validate(ai, raw, supplements);

        assertTrue(out.missingFields().contains("rollbackPlan"));
        assertTrue(out.riskFactors().contains("rollback_plan_missing"));
        assertTrue(out.corrections().stream().noneMatch(c -> c.startsWith("[SUP]")));
    }

    @Test
    @DisplayName("빈 supplements → 기존 2-인자 validate 와 동일 결과")
    void emptySupplements_sameAsOldPath() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "14:00", Priority.P2, "DB_OPERATION",
                "users", true, false,
                List.of("rollbackPlan"),
                List.of("database_schema_change", "rollback_plan_missing"),
                List.of(), 0.85);
        String raw = "운영 users 컬럼 추가. 롤백 없음.";

        TriageResult oldPath = validator.validate(ai, raw);
        TriageResult newPath = validator.validate(ai, raw, Map.of());

        assertEquals(oldPath.missingFields(), newPath.missingFields());
        assertEquals(oldPath.riskFactors(), newPath.riskFactors());
        assertEquals(oldPath.corrections(), newPath.corrections());
    }

    // --- helper (TriageValidatorTest 와 동일 패턴) ---
    private static TriageResult aiResult(RequestType type, String category, EnvironmentType env,
                                         String requestedTime, Priority priority, String team,
                                         String affectedService, Boolean testPlan, Boolean rollback,
                                         List<String> missing, List<String> factors,
                                         List<Evidence> evidence, double confidence) {
        return new TriageResult(type, category, env, requestedTime, priority, team, affectedService,
                testPlan, rollback, missing, factors, evidence,
                List.of(), confidence, null);
    }
}
