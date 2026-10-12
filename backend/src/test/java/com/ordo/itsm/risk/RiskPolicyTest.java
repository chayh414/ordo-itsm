package com.ordo.itsm.risk;

import com.ordo.itsm.ticket.RiskGrade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 위험도 점수 규칙 v1 단위 테스트. 순수 로직이라 Spring 없이 돌아간다.
 * 입력은 TriageValidator 가 보정한 "최종" riskFactors 를 가정한다 (AI 원본 아님).
 */
class RiskPolicyTest {

    @Test
    @DisplayName("대표 시나리오: 운영 + 스키마 + 롤백 누락 + 심야 = 9점 CRITICAL")
    void criticalScenario() {
        RiskPolicy.Outcome out = RiskPolicy.score(List.of(
                "production_environment",
                "database_schema_change",
                "rollback_plan_missing",
                "night_work"));
        assertEquals(9, out.score());
        assertEquals(RiskGrade.CRITICAL, out.grade());
        assertEquals(3, out.factorPoints().get("production_environment"));
        assertEquals(3, out.factorPoints().get("database_schema_change"));
        assertEquals(2, out.factorPoints().get("rollback_plan_missing"));
        assertEquals(1, out.factorPoints().get("night_work"));
    }

    @Test
    @DisplayName("운영 + 스키마 + 롤백 누락 (낮 시간) = 8점 HIGH")
    void highScenarioWithRollbackMissing() {
        RiskPolicy.Outcome out = RiskPolicy.score(List.of(
                "production_environment",
                "database_schema_change",
                "rollback_plan_missing"));
        assertEquals(8, out.score());
        assertEquals(RiskGrade.HIGH, out.grade());
        assertFalse(out.factorPoints().containsKey("night_work"));
    }

    @Test
    @DisplayName("운영 + 스키마, 롤백·테스트 있음 = 6점 HIGH (경계값)")
    void highBoundary6() {
        RiskPolicy.Outcome out = RiskPolicy.score(List.of(
                "production_environment",
                "database_schema_change"));
        assertEquals(6, out.score());
        assertEquals(RiskGrade.HIGH, out.grade());
    }

    @Test
    @DisplayName("개발 환경 스키마 변경 = 3점 MEDIUM (운영 요인 없음)")
    void mediumBoundary3() {
        RiskPolicy.Outcome out = RiskPolicy.score(List.of("database_schema_change"));
        assertEquals(3, out.score());
        assertEquals(RiskGrade.MEDIUM, out.grade());
    }

    @Test
    @DisplayName("요인 비어있음 = 0점 LOW")
    void zeroPoints() {
        RiskPolicy.Outcome out = RiskPolicy.score(List.of());
        assertEquals(0, out.score());
        assertEquals(RiskGrade.LOW, out.grade());
        assertTrue(out.factorPoints().isEmpty());
    }

    @Test
    @DisplayName("vocab 밖 요인은 0점 (임의 가산 금지)")
    void unknownFactorsIgnored() {
        RiskPolicy.Outcome out = RiskPolicy.score(List.of(
                "production_environment",
                "spurious_factor",
                "another_unknown"));
        assertEquals(3, out.score());
        assertEquals(RiskGrade.MEDIUM, out.grade());
        assertEquals(1, out.factorPoints().size());
    }

    @Test
    @DisplayName("중복 요인은 1번만 가산")
    void duplicatesIgnored() {
        RiskPolicy.Outcome out = RiskPolicy.score(List.of(
                "production_environment", "production_environment",
                "night_work", "night_work"));
        assertEquals(4, out.score());
        assertEquals(RiskGrade.MEDIUM, out.grade());
    }

    @Test
    @DisplayName("등급 경계값: 8=HIGH, 9=CRITICAL, 5=MEDIUM, 6=HIGH, 2=LOW, 3=MEDIUM")
    void gradeBoundaries() {
        assertEquals(RiskGrade.LOW, RiskPolicy.gradeFor(0));
        assertEquals(RiskGrade.LOW, RiskPolicy.gradeFor(2));
        assertEquals(RiskGrade.MEDIUM, RiskPolicy.gradeFor(3));
        assertEquals(RiskGrade.MEDIUM, RiskPolicy.gradeFor(5));
        assertEquals(RiskGrade.HIGH, RiskPolicy.gradeFor(6));
        assertEquals(RiskGrade.HIGH, RiskPolicy.gradeFor(8));
        assertEquals(RiskGrade.CRITICAL, RiskPolicy.gradeFor(9));
        assertEquals(RiskGrade.CRITICAL, RiskPolicy.gradeFor(99));
    }
}
