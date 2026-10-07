package com.ordo.itsm.triage;

import com.ordo.itsm.ticket.EnvironmentType;
import com.ordo.itsm.ticket.Priority;
import com.ordo.itsm.ticket.RequestType;
import com.ordo.itsm.triage.TriageResult.Evidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TriageValidator의 보정 규칙 테스트.
 * Spring 컨텍스트/DB 없이 순수 JUnit 5로 실행 가능 (POJO 검증기).
 */
class TriageValidatorTest {

    private final TriageValidator validator = new TriageValidator();

    @Test
    @DisplayName("방화벽 3306+운영+오늘 밤: FIX 다수(requestType·factor·시각 환각) + FILL(IP, scheduledTime), 확신도 하향")
    void firewallProductionTonight_appliesFixAndFill() {
        TriageResult ai = aiResult(
                RequestType.SERVICE_REQUEST, "FIREWALL", EnvironmentType.PRODUCTION,
                "22:00", Priority.P3, "NETWORK_SECURITY",
                null, null, null,
                List.of(), List.of("night_work"),
                List.of(new Evidence("night_work", "오늘 밤")),
                1.0);
        String raw = "운영 환경에서 3306 포트 열어주세요. 오늘 밤 작업 예정.";

        TriageResult out = validator.validate(ai, raw);

        assertEquals(RequestType.CHANGE, out.requestType(), "SERVICE_REQUEST→CHANGE 보정");
        assertNull(out.requestedTime(), "원문에 숫자 시각 없음 → 환각 제거");
        assertTrue(out.riskFactors().containsAll(List.of("firewall_change", "production_environment", "night_work")),
                "자동 유도된 factor + 밤 근거 유지");
        assertTrue(out.missingFields().containsAll(List.of("sourceIp", "destinationIp", "scheduledTime")),
                "패턴 없는 필수 항목 자동 보완");
        assertFalse(out.missingFields().contains("port"), "'3306 포트'는 포트 제공으로 간주");
        assertTrue(out.confidence() < 0.7, "FIX 발생 → 확신도 하향");
        assertCorrectionsContain(out, "[FIX]", "requestType", "CHANGE");
        assertCorrectionsContain(out, "[FIX]", "firewall_change");
        assertCorrectionsContain(out, "[FIX]", "production_environment");
        assertCorrectionsContain(out, "[FIX]", "requestedTime", "22:00");
        assertCorrectionsContain(out, "[FILL]", "sourceIp");
        assertCorrectionsContain(out, "[FILL]", "scheduledTime");
    }

    @Test
    @DisplayName("운영 DB 23시 컬럼 추가(롤백 없음): AI가 다 맞춤 → corrections 비어있고 confidence 유지")
    void dbSchemaChange_aiCorrect_noCorrections() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "23:00", Priority.P2, "DB_OPERATION",
                "users 테이블", true, false,
                List.of("rollbackPlan"),
                List.of("database_schema_change", "production_environment", "rollback_plan_missing", "night_work"),
                List.of(
                        new Evidence("database_schema_change", "컬럼 추가"),
                        new Evidence("production_environment", "운영"),
                        new Evidence("rollback_plan_missing", "롤백 계획 없음"),
                        new Evidence("night_work", "23시")
                ),
                0.85);
        String raw = "운영 데이터베이스에 users 테이블에 컬럼 추가. 23시 작업 예정. 테스트 완료. 롤백 계획 없음.";

        TriageResult out = validator.validate(ai, raw);

        assertEquals(RequestType.CHANGE, out.requestType());
        assertEquals("23:00", out.requestedTime(), "원문 시각과 일치 → 유지");
        assertTrue(out.corrections().isEmpty(), "모든 보정 불필요 — corrections 비어있음");
        assertEquals(0.85, out.confidence(), 1e-9, "FIX 없음 → 확신도 유지");
    }

    @Test
    @DisplayName("원문 23시 vs AI 22:00: 원문 기준으로 23:00 교정 + 확신도 하향")
    void requestedTime_mismatch_correctedToTextHour() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "22:00", Priority.P2, "DB_OPERATION",
                "users 테이블", true, false,
                List.of("rollbackPlan"),
                List.of("database_schema_change", "production_environment", "rollback_plan_missing", "night_work"),
                List.of(),
                0.9);
        String raw = "운영 DB users 테이블 컬럼 추가. 23시 작업. 테스트 완료. 롤백 계획 없음.";

        TriageResult out = validator.validate(ai, raw);

        assertEquals("23:00", out.requestedTime(), "원문 23시로 교정");
        assertTrue(out.confidence() < 0.7, "FIX 발생 → 확신도 하향");
        assertCorrectionsContain(out, "[FIX]", "requestedTime", "22:00", "23:00");
    }

    @Test
    @DisplayName("INCIDENT: occurredAt·errorMessage FILL만 — confidence 유지")
    void incident_fillOnly_confidenceKept() {
        TriageResult ai = aiResult(
                RequestType.INCIDENT, "SERVICE_OUTAGE", null,
                null, Priority.P2, "APPLICATION_OPERATION",
                "주문 시스템", null, null,
                List.of(), List.of(), List.of(),
                0.9);
        String raw = "주문 시스템 안 됩니다. 접속이 안 돼요.";

        TriageResult out = validator.validate(ai, raw);

        assertEquals(RequestType.INCIDENT, out.requestType());
        assertTrue(out.missingFields().contains("occurredAt"), "시각 근거 없음 → occurredAt FILL");
        assertTrue(out.missingFields().contains("errorMessage"), "오류 메시지 근거 없음 → errorMessage FILL");
        assertFalse(out.missingFields().contains("affectedService"), "affectedService는 자동 보완 대상 아님");
        assertFalse(out.missingFields().contains("impactScope"), "impactScope는 자동 보완 대상 아님");
        assertEquals(0.9, out.confidence(), 1e-9, "FILL만 있음 → 확신도 유지");
        assertTrue(out.corrections().stream().noneMatch(c -> c.startsWith("[FIX]")),
                "FIX 보정이 없어야 함");
        assertCorrectionsContain(out, "[FILL]", "occurredAt");
        assertCorrectionsContain(out, "[FILL]", "errorMessage");
    }

    @Test
    @DisplayName("정상 요청(스테이징 DB 14시, 롤백 명시 누락): corrections 비어있음")
    void clean_noCorrections() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.STAGING,
                "14:00", Priority.P3, "DB_OPERATION",
                "users 테이블", true, false,
                List.of("rollbackPlan"),
                List.of("database_schema_change", "rollback_plan_missing"),
                List.of(
                        new Evidence("database_schema_change", "컬럼 추가"),
                        new Evidence("rollback_plan_missing", "롤백 계획 없음")
                ),
                0.85);
        String raw = "스테이징 DB users 테이블 컬럼 추가. 테스트 완료. 롤백 계획 없음. 14시 작업.";

        TriageResult out = validator.validate(ai, raw);

        assertTrue(out.corrections().isEmpty(), "AI가 다 맞춘 요청 — corrections 비어있음");
        assertEquals("14:00", out.requestedTime());
        assertEquals(0.85, out.confidence(), 1e-9);
        assertEquals(RequestType.CHANGE, out.requestType());
    }

    @Test
    @DisplayName("T6: 대표 시연 문장 '테스트는 했지만 롤백 … 아직 못' — topic-boundary 가드로 '아직 못'은 롤백 쪽으로만 매치")
    void testPlanMissing_isRemoved_whenEvidencePresent() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "14:00", Priority.P2, "DB_OPERATION",
                "users 테이블", true, false,
                List.of("rollbackPlan"),
                List.of("database_schema_change", "production_environment",
                        "test_plan_missing", "rollback_plan_missing"),
                List.of(
                        new Evidence("database_schema_change", "컬럼 추가"),
                        new Evidence("production_environment", "운영"),
                        new Evidence("test_plan_missing", ""),
                        new Evidence("rollback_plan_missing", "아직 정리하지 못")
                ),
                0.8);
        String raw = "운영 DB users 테이블에 컬럼 추가. 14시 작업. 테스트는 했지만 롤백 절차는 아직 정리하지 못했습니다.";

        TriageResult out = validator.validate(ai, raw);

        assertFalse(out.riskFactors().contains("test_plan_missing"),
                "원문 '테스트는 했' + testPlanProvided=TRUE → test_plan_missing 제거");
        assertTrue(out.riskFactors().contains("rollback_plan_missing"),
                "원문 '롤백 … 아직 정리하지 못' → 부정 표현 매치, rollback_plan_missing 유지");
        assertFalse(out.missingFields().contains("testPlan"), "testPlan은 missingFields에 없어야 함");
        assertTrue(out.missingFields().contains("rollbackPlan"));
        assertCorrectionsContain(out, "[FIX]", "test_plan_missing");
        assertTrue(out.confidence() < 0.7, "FIX 발생 → 확신도 하향");
    }

    @Test
    @DisplayName("T7: INCIDENT에 CHANGE 전용 factor(production_environment)가 붙으면 [NORM]으로 정리, confidence 유지")
    void incident_riskFactors_areCleared_withNormPrefix() {
        TriageResult ai = aiResult(
                RequestType.INCIDENT, "SERVICE_OUTAGE", EnvironmentType.PRODUCTION,
                null, Priority.P1, "APPLICATION_OPERATION",
                "결제 시스템", null, null,
                List.of(),
                List.of("production_environment", "night_work"),
                List.of(
                        new Evidence("production_environment", "운영 결제 시스템"),
                        new Evidence("night_work", "새벽부터")
                ),
                0.9);
        String raw = "운영 결제 시스템이 새벽부터 안 됩니다. 500 에러 발생.";

        TriageResult out = validator.validate(ai, raw);

        assertEquals(RequestType.INCIDENT, out.requestType());
        assertTrue(out.riskFactors().isEmpty(), "non-CHANGE는 riskFactors 전부 clear");
        assertTrue(out.evidence().isEmpty(), "evidence도 함께 비움");
        assertTrue(out.corrections().stream().anyMatch(c -> c.startsWith("[NORM]")),
                "[NORM] 로그가 남아야 함");
        assertTrue(out.corrections().stream().noneMatch(c -> c.startsWith("[FIX]")),
                "[FIX] 없음 → confidence 유지");
        assertEquals(0.9, out.confidence(), 1e-9);
    }

    @Test
    @DisplayName("T8: testPlanProvided=FALSE + 원문 '테스트 완료' → test_plan_missing과 testPlan 둘 다 제거")
    void testPlanMissing_isRemoved_evenWhenAIClaimsFalse_ifEvidencePresent() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "14:00", Priority.P2, "DB_OPERATION",
                "users 테이블", false, false,     // AI가 testPlanProvided=FALSE로 잘못 알림
                List.of("testPlan", "rollbackPlan"),
                List.of("database_schema_change", "production_environment",
                        "test_plan_missing", "rollback_plan_missing"),
                List.of(),
                0.7);
        String raw = "운영 DB users 테이블 컬럼 추가. 14시 작업. 테스트 완료. 롤백 절차는 아직 정리 못 했음.";

        TriageResult out = validator.validate(ai, raw);

        assertFalse(out.riskFactors().contains("test_plan_missing"),
                "원문에 '테스트 완료' 근거 → test_plan_missing 제거 (AI의 FALSE 주장 무시)");
        assertFalse(out.missingFields().contains("testPlan"),
                "testPlan도 missingFields에서 제거");
        assertTrue(out.riskFactors().contains("rollback_plan_missing"),
                "rollback 부정 표현 '아직 … 못' → 유지");
        assertTrue(out.missingFields().contains("rollbackPlan"));
    }

    @Test
    @DisplayName("T10: 원문에 '테스트는 아직 못' 부정 → AI가 testPlanProvided=TRUE라고 해도 원문 우선, test_plan_missing·testPlan 추가")
    void testPlanNegated_overridesAITrue() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "14:00", Priority.P2, "DB_OPERATION",
                "users 테이블", true, false,     // AI가 testPlanProvided=TRUE로 잘못 알림
                List.of("rollbackPlan"),         // AI는 testPlan을 missing에 안 넣음 (TRUE라고 믿었기 때문)
                List.of("database_schema_change", "production_environment", "rollback_plan_missing"),
                List.of(),
                0.85);
        String raw = "운영 DB 컬럼 추가합니다. 테스트는 아직 못 했습니다.";

        TriageResult out = validator.validate(ai, raw);

        assertTrue(out.riskFactors().contains("test_plan_missing"),
                "원문 '아직 못' → AI TRUE를 무시하고 test_plan_missing 추가되어야 함");
        assertTrue(out.missingFields().contains("testPlan"),
                "testPlan도 missingFields에 추가");
        assertCorrectionsContain(out, "[FIX]", "test_plan_missing", "원문에 테스트 미완료");
        assertTrue(out.confidence() < 0.7, "FIX 발생 → 확신도 하향");
    }

    @Test
    @DisplayName("T12: '롤백 … 준비했지만 테스트 … 아직 못' — T6의 거울 케이스. topic-boundary 가드로 '아직 못'은 테스트 쪽으로만 매치")
    void rollbackDone_butTestNotDone() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "14:00", Priority.P2, "DB_OPERATION",
                "users 테이블", true, true,          // AI는 둘 다 TRUE라고 잘못 알림
                List.of(),
                List.of("database_schema_change", "production_environment"),
                List.of(),
                0.85);
        String raw = "운영 DB users 테이블 컬럼 추가. 14시 작업. 롤백 스크립트는 준비했지만 테스트는 아직 못 했습니다.";

        TriageResult out = validator.validate(ai, raw);

        assertFalse(out.riskFactors().contains("rollback_plan_missing"),
                "'롤백 … 준비했지만' + '아직 못'은 테스트 쪽 → rollback_plan_missing 없음");
        assertFalse(out.missingFields().contains("rollbackPlan"));
        assertTrue(out.riskFactors().contains("test_plan_missing"),
                "'테스트는 아직 못' → test_plan_missing 추가되어야 함");
        assertTrue(out.missingFields().contains("testPlan"));
        assertCorrectionsContain(out, "[FIX]", "test_plan_missing", "원문에 테스트 미완료");
        assertTrue(out.confidence() < 0.7, "FIX 발생 → 확신도 하향");
    }

    @Test
    @DisplayName("T11: 테스트·롤백 둘 다 완료 → test_plan_missing·rollback_plan_missing 둘 다 없음 (정상 요청 회귀)")
    void bothPlansCompleted_noMissingFlags() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "14:00", Priority.P2, "DB_OPERATION",
                "users 테이블", true, true,
                List.of(),
                List.of("database_schema_change", "production_environment"),
                List.of(
                        new Evidence("database_schema_change", "컬럼 추가"),
                        new Evidence("production_environment", "운영")
                ),
                0.9);
        String raw = "운영 DB users 테이블 컬럼 추가. 14시 작업. 테스트 완료했고 롤백 스크립트도 준비했습니다.";

        TriageResult out = validator.validate(ai, raw);

        assertFalse(out.riskFactors().contains("test_plan_missing"));
        assertFalse(out.riskFactors().contains("rollback_plan_missing"));
        assertFalse(out.missingFields().contains("testPlan"));
        assertFalse(out.missingFields().contains("rollbackPlan"));
        assertTrue(out.corrections().isEmpty(), "AI가 모두 맞춤 — corrections 비어있음");
        assertEquals(0.9, out.confidence(), 1e-9, "FIX 없음 — 확신도 유지");
    }

    @Test
    @DisplayName("T9: ROLLBACK_NEGATED는 마침표를 넘지 않음 — '롤백 첨부. 테스트 아직 못' 에서 '아직 못'이 롤백 부정으로 잡히면 안 됨")
    void rollbackNegated_doesNotCrossSentenceBoundary() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "14:00", Priority.P2, "DB_OPERATION",
                "users 테이블", false, false,
                List.of("testPlan", "rollbackPlan"),
                List.of("database_schema_change", "production_environment",
                        "test_plan_missing", "rollback_plan_missing"),
                List.of(),
                0.75);
        String raw = "운영 DB users 테이블 컬럼 추가. 14시 작업. 롤백 계획은 첨부했습니다. 테스트는 아직 못 했습니다.";

        TriageResult out = validator.validate(ai, raw);

        assertFalse(out.riskFactors().contains("rollback_plan_missing"),
                "'롤백 … 첨부' + 부정은 다음 문장 — rollback_plan_missing 제거되어야 함");
        assertFalse(out.missingFields().contains("rollbackPlan"),
                "rollbackPlan도 missingFields에서 제거");
        assertCorrectionsContain(out, "[FIX]", "rollback_plan_missing");
    }

    @Test
    @DisplayName("엔진이 corrections 값을 보내도 Validator가 무시하고 빈 리스트로 시작")
    void enginesCorrections_areIgnored() {
        // AI가 json_object 폴백 등에서 임의로 꾸며낸 corrections를 보냈다고 가정.
        // 보정이 전혀 필요 없는 정상 입력이므로, 결과 corrections는 반드시 비어있어야 한다.
        List<String> aiFabricated = List.of(
                "[FIX] 가짜 보정 — AI가 임의 생성",
                "[FILL] 거짓 보완"
        );
        TriageResult ai = new TriageResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.STAGING,
                "14:00", Priority.P3, "DB_OPERATION",
                "users 테이블", true, false,
                List.of("rollbackPlan"),
                List.of("database_schema_change", "rollback_plan_missing"),
                List.of(),
                List.of(),
                0.85,
                aiFabricated     // 엔진이 억지로 채워 보낸 상황
        );
        String raw = "스테이징 DB users 테이블 컬럼 추가. 테스트 완료. 롤백 계획 없음. 14시 작업.";

        TriageResult out = validator.validate(ai, raw);

        assertTrue(out.corrections().isEmpty(),
                "Validator가 보정 불필요라고 판단했으면 corrections는 비어있어야 함 — 엔진이 보낸 값 반영 금지. 실제: " + out.corrections());
        assertTrue(out.corrections().stream().noneMatch(c -> c.contains("가짜") || c.contains("거짓")),
                "엔진이 보낸 corrections 항목이 섞여 있으면 안 됨");
    }

    @Test
    @DisplayName("T13: 대표 시연 문장 — AI가 night_work·database_schema_change 빠뜨리고 test_plan_missing 넣어도 최종 factors는 수렴")
    void demoSentence_convergesToStableFactors() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "23:00", Priority.P2, "DB_OPERATION",
                "학생 포털 테이블", true, false,
                List.of("rollbackPlan"),
                List.of("production_environment", "test_plan_missing", "rollback_plan_missing"),
                List.of(),
                0.75);
        String raw = "오늘 23시에 운영 DB 학생 포털 테이블에 컬럼을 추가하려고 합니다. 테스트는 했지만 롤백 절차는 아직 정리하지 못했습니다.";

        TriageResult out = validator.validate(ai, raw);

        assertEquals(
                new java.util.LinkedHashSet<>(List.of(
                        "production_environment", "database_schema_change",
                        "rollback_plan_missing", "night_work")),
                new java.util.LinkedHashSet<>(out.riskFactors()),
                "대표 시연 문장의 최종 factors는 AI 흔들림과 무관하게 정확히 이 4개로 수렴");
        assertEquals(List.of("rollbackPlan"), out.missingFields());
        assertEquals("23:00", out.requestedTime(), "AI 23:00이 확정 시각 {23}에 일치 → 유지");
        assertTrue(out.confidence() < 0.7);
    }

    @Test
    @DisplayName("T14: '내일 2시' 모호 시각 — AI 14:00 유지, night_work 없음")
    void ambiguousBareHour_matchesPmCandidate_noNight() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "14:00", Priority.P2, "DB_OPERATION",
                "users 테이블", true, true,
                List.of(),
                List.of("database_schema_change", "production_environment"),
                List.of(),
                0.9);
        String raw = "내일 2시에 운영 DB users 테이블 인덱스 추가합니다. 테스트 완료, 롤백 스크립트 준비했습니다.";

        TriageResult out = validator.validate(ai, raw);

        assertEquals("14:00", out.requestedTime(), "AI 14가 모호 후보 {2,14}에 포함 → 유지");
        assertFalse(out.riskFactors().contains("night_work"),
                "모호한 시각은 야간 근거로 쓰지 않음 → night_work 없음");
        assertTrue(out.corrections().stream().noneMatch(c -> c.contains("night_work")),
                "night_work 보정 없음");
    }

    @Test
    @DisplayName("T15: '새벽 2시' 확정 시각 — AI 02:00 유지, night_work 추가")
    void confirmedEarlyMorning_addsNightWork() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "02:00", Priority.P2, "DB_OPERATION",
                "운영 DB", true, true,
                List.of(),
                List.of("database_schema_change", "production_environment"),
                List.of(),
                0.9);
        String raw = "새벽 2시에 운영 DB 작업합니다.";

        TriageResult out = validator.validate(ai, raw);

        assertEquals("02:00", out.requestedTime(), "'새벽 2시' → 확정 {2}, AI 2 매치 → 유지");
        assertTrue(out.riskFactors().contains("night_work"),
                "'새벽' NIGHT_WORDS + 확정 2시 야간대 → night_work 추가");
        assertCorrectionsContain(out, "[FIX]", "night_work", "심야");
    }

    @Test
    @DisplayName("T16: '오후 11시' 확정 시각 — AI 23:00 유지, night_work 추가")
    void confirmedPmHour_addsNightWork() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "FIREWALL", EnvironmentType.PRODUCTION,
                "23:00", Priority.P2, "NETWORK_SECURITY",
                null, null, null,
                List.of("sourceIp", "destinationIp", "port"),
                List.of("firewall_change", "production_environment"),
                List.of(),
                0.8);
        String raw = "오후 11시에 방화벽 작업";

        TriageResult out = validator.validate(ai, raw);

        assertEquals("23:00", out.requestedTime(), "'오후 11시' → 확정 {23}, AI 23 매치 → 유지");
        assertTrue(out.riskFactors().contains("night_work"),
                "확정 23시(야간대) → night_work 추가");
        assertCorrectionsContain(out, "[FIX]", "night_work", "심야");
    }

    @Test
    @DisplayName("T17: '밤 2시' → 새벽대 02시로 해석. AI 02:00 유지, 14:00으로 교정되면 안 됨, night_work 있음")
    void bam_hour1To5_isEarlyMorning() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "DATABASE_SCHEMA", EnvironmentType.PRODUCTION,
                "02:00", Priority.P2, "DB_OPERATION",
                "운영 DB", true, true,
                List.of(),
                List.of("database_schema_change", "production_environment"),
                List.of(),
                0.9);
        String raw = "밤 2시에 운영 DB 작업합니다.";

        TriageResult out = validator.validate(ai, raw);

        assertEquals("02:00", out.requestedTime(),
                "'밤 2시'는 새벽 02시로 확정 → AI 02:00 매치, 14:00으로 교정 금지");
        assertTrue(out.riskFactors().contains("night_work"),
                "'밤' NIGHT_WORDS + 확정 2시 야간대 → night_work 추가");
        assertTrue(out.corrections().stream().noneMatch(c -> c.contains("requestedTime:")),
                "requestedTime 교정 로그 없음");
    }

    @Test
    @DisplayName("T18: '밤 12시' → 자정 00시. AI 00:00 유지, night_work 있음")
    void bam_12_isMidnight() {
        TriageResult ai = aiResult(
                RequestType.CHANGE, "FIREWALL", EnvironmentType.PRODUCTION,
                "00:00", Priority.P2, "NETWORK_SECURITY",
                null, null, null,
                List.of("sourceIp", "destinationIp", "port"),
                List.of("firewall_change", "production_environment"),
                List.of(),
                0.8);
        String raw = "밤 12시에 방화벽 작업";

        TriageResult out = validator.validate(ai, raw);

        assertEquals("00:00", out.requestedTime(),
                "'밤 12시'는 자정 00시로 확정 → AI 00:00 매치");
        assertTrue(out.riskFactors().contains("night_work"),
                "자정은 야간대 + '밤' NIGHT_WORDS → night_work 추가");
    }

    // --- helpers ---

    private static TriageResult aiResult(RequestType type, String category, EnvironmentType env,
                                         String requestedTime, Priority priority, String team,
                                         String affectedService, Boolean testPlan, Boolean rollback,
                                         List<String> missing, List<String> factors,
                                         List<Evidence> evidence, double confidence) {
        return new TriageResult(type, category, env, requestedTime, priority, team, affectedService,
                testPlan, rollback, missing, factors, evidence,
                List.of(),      // clarifyingQuestions — validator가 재생성
                confidence,
                null            // corrections — validator가 무시
        );
    }

    private static void assertCorrectionsContain(TriageResult out, String... parts) {
        boolean hit = out.corrections().stream().anyMatch(c -> {
            for (String p : parts) if (!c.contains(p)) return false;
            return true;
        });
        assertTrue(hit, "corrections 중 '" + String.join(" + ", parts) + "' 포함 기대. 실제: " + out.corrections());
    }
}
