package com.ordo.itsm.triage;

import com.ordo.itsm.ticket.EnvironmentType;
import com.ordo.itsm.ticket.RequestType;
import com.ordo.itsm.triage.TriageResult.Evidence;
import com.ordo.itsm.triage.engine.TriageEngineException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 결과를 그대로 믿지 않고 서버가 한 번 더 검증·보정한다 (요청 처리 ④단계).
 * - 허용 목록 밖의 값 제거
 * - 분류·환경 간 일관성 보정 ([FIX])
 * - 환각 시각 제거/교정 ([FIX])
 * - 유형·분류별 필수 항목 중 패턴으로 "없음"이 확실한 것만 missing에 추가 ([FILL])
 * - [FIX] 발생 시 확신도 하향 (AI 자체 confidence 신뢰 금지)
 *
 * corrections는 Validator에서 항상 새로 만든다. 엔진 응답이 전달한 값은 무시한다.
 */
@Component
public class TriageValidator {

    /** category만으로 변경(CHANGE)임이 자명한 분류 → requestType 보정 트리거 */
    private static final Set<String> CHANGE_CATEGORIES = Set.of(
            "FIREWALL", "NETWORK", "DATABASE_SCHEMA", "DATABASE_PERMISSION", "DATABASE_PATCH");

    /** category에서 자동 유도되는 위험 요인 (DATABASE_PATCH는 대응 factor가 vocab에 없어 생략) */
    private static final Map<String, String> CATEGORY_IMPLIED_FACTOR = Map.of(
            "FIREWALL", "firewall_change",
            "NETWORK", "network_change",
            "DATABASE_SCHEMA", "database_schema_change",
            "DATABASE_PERMISSION", "database_permission_change"
    );

    /** 패턴으로 확실히 "없음" 판정이 가능한 필드만 자동 보완. 나머지는 AI 판단 유지. */
    private static final Set<String> AUTO_FILLABLE = Set.of(
            "scheduledTime", "environment", "testPlan", "rollbackPlan",
            "sourceIp", "destinationIp", "port", "occurredAt", "errorMessage");

    // --- 패턴 ---
    private static final Pattern HHMM = Pattern.compile("(\\d{1,2}):(\\d{2})");
    /** "N시" — 뒤에 "간"이 붙으면(시간, 시간대) 매치 제외. 선택 접두 오전/오후/AM/PM 포함. */
    private static final Pattern HOUR_WORD = Pattern.compile(
            "(오전|오후|AM|PM|am|pm)?\\s*(\\d{1,2})\\s*시(?!간)");
    private static final Pattern NIGHT_WORDS = Pattern.compile("심야|새벽|야간|밤");
    private static final Pattern IP_PATTERN = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b");
    private static final Pattern PORT_PATTERN = Pattern.compile("\\d{2,5}\\s?(?:번\\s?)?포트|포트\\s?\\d{2,5}|:\\d{2,5}");
    // 주제 경계 가드 — 같은 문장 안에서도 "다른 주제 단어(롤백/테스트)" 전까지만 탐색한다.
    // 예: "테스트는 했지만 롤백 … 아직 못" 에서 '아직 못'은 롤백 쪽 부정이므로 테스트 부정으로 잡지 않음.
    private static final Pattern TEST_PLAN_WORDS = Pattern.compile(
            "테스트(?:(?!롤백|원복)[^.\\n]){0,15}(?:완료|했|진행|검증)");
    private static final Pattern TEST_PLAN_NEGATED = Pattern.compile(
            "테스트(?:(?!롤백|원복)[^.\\n]){0,20}(?:못|안\\s|아직|미정|없|불가)");
    private static final Pattern ROLLBACK_WORDS = Pattern.compile("롤백|원복");
    private static final Pattern ROLLBACK_NEGATED = Pattern.compile(
            "(?:롤백|원복)(?:(?!테스트)[^.\\n]){0,20}(?:없|못|아직|미정|안\\s|불가)");
    private static final Pattern ERROR_WORDS = Pattern.compile(
            "오류|에러|exception|실패|\\d{3}\\s?(?:에러|error)|error\\s*[:=]|error\\s+\\w", Pattern.CASE_INSENSITIVE);
    private static final Pattern OCCURRED_WORDS = Pattern.compile(
            "오늘|어제|방금|부터|이후|이전|오전|오후|새벽|아침|점심|저녁|밤|심야|야간");
    private static final Pattern ENV_WORDS = Pattern.compile(
            "운영|프로덕션|실서버|스테이징|개발|dev|prod|staging", Pattern.CASE_INSENSITIVE);

    public TriageResult validate(TriageResult r, String rawText) {
        if (r == null || r.requestType() == null) {
            throw new TriageEngineException(TriageEngineException.Kind.INVALID_JSON,
                    "requestType이 없습니다.", null, null);
        }

        String text = rawText == null ? "" : rawText;
        List<String> corrections = new ArrayList<>();

        // 1) 허용 목록 밖 값 제거
        String category = TriageVocabulary.CATEGORIES.contains(r.category()) ? r.category() : "OTHER";
        String team = TriageVocabulary.TEAMS.contains(r.recommendedTeam()) ? r.recommendedTeam() : "SERVICE_DESK";
        Set<String> missing = new LinkedHashSet<>(filter(r.missingFields(), TriageVocabulary.FIELD_QUESTIONS.keySet()));
        Set<String> factors = new LinkedHashSet<>(filter(r.riskFactors(), Set.copyOf(TriageVocabulary.RISK_FACTORS)));
        RequestType requestType = r.requestType();

        // 2) Rule 1 — 일관성 보정
        if (CHANGE_CATEGORIES.contains(category) && requestType != RequestType.CHANGE) {
            corrections.add("[FIX] requestType=" + requestType + "→CHANGE (category=" + category + ")");
            requestType = RequestType.CHANGE;
        }
        String impliedFactor = CATEGORY_IMPLIED_FACTOR.get(category);
        if (impliedFactor != null && factors.add(impliedFactor)) {
            corrections.add("[FIX] riskFactor+" + impliedFactor + " (category=" + category + ")");
        }
        if (requestType == RequestType.CHANGE && r.environment() == EnvironmentType.PRODUCTION
                && factors.add("production_environment")) {
            corrections.add("[FIX] riskFactor+production_environment (CHANGE+PRODUCTION)");
        }

        // 3) Rule 2 — 시각 환각 차단/교정
        List<Integer> textHours = extractHours(text);
        String requestedTime = r.requestedTime();
        if (requestedTime != null) {
            Integer aiHour = parseHour(requestedTime);
            if (textHours.isEmpty()) {
                corrections.add("[FIX] requestedTime=" + requestedTime + "→null (원문에 시각 근거 없음)");
                requestedTime = null;
            } else if (aiHour != null && !textHours.contains(aiHour)) {
                String corrected = String.format("%02d:00", textHours.get(0));
                corrections.add("[FIX] requestedTime: " + requestedTime + "→" + corrected + " (원문 기준)");
                requestedTime = corrected;
            }
        }
        if (factors.contains("night_work")) {
            boolean nightOk = NIGHT_WORDS.matcher(text).find()
                    || isNightHour(requestedTime)
                    || textHours.stream().anyMatch(h -> h >= 22 || h < 6);
            if (!nightOk) {
                factors.remove("night_work");
                corrections.add("[FIX] riskFactor-night_work (근거 없음)");
            }
        }

        // 4) rollback/test 일치성 (CHANGE에서만) :
        //    원칙 — 제거는 "AI TRUE 또는 완료 표현" AND "부정 표현 없음"일 때만.
        //    애매하면 유지(위험 쪽). AI TRUE라도 원문 부정이 있으면 부정을 우선한다.
        if (requestType == RequestType.CHANGE) {
            boolean testNegated = TEST_PLAN_NEGATED.matcher(text).find();
            boolean rollbackNegated = ROLLBACK_NEGATED.matcher(text).find();

            boolean testEvidence = (Boolean.TRUE.equals(r.testPlanProvided())
                    || TEST_PLAN_WORDS.matcher(text).find()) && !testNegated;
            boolean rollbackEvidence = (Boolean.TRUE.equals(r.rollbackPlanProvided())
                    || ROLLBACK_WORDS.matcher(text).find()) && !rollbackNegated;

            // 제거 ([FIX]) — 확실한 근거일 때만
            if (testEvidence) {
                if (factors.remove("test_plan_missing")) {
                    corrections.add("[FIX] riskFactor-test_plan_missing (테스트 수행 근거 있음)");
                }
                if (missing.remove("testPlan")) {
                    corrections.add("[FIX] missingField-testPlan (테스트 수행 근거 있음)");
                }
            }
            if (rollbackEvidence) {
                if (factors.remove("rollback_plan_missing")) {
                    corrections.add("[FIX] riskFactor-rollback_plan_missing (롤백 계획 근거 있음)");
                }
                if (missing.remove("rollbackPlan")) {
                    corrections.add("[FIX] missingField-rollbackPlan (롤백 계획 근거 있음)");
                }
            }

            // 추가 — AI FALSE면 [FILL], 원문 부정만 있고 AI는 TRUE/null이면 [FIX]
            addMissingWithEvidence(missing, factors, corrections,
                    "testPlan", "test_plan_missing", "testPlanProvided",
                    Boolean.FALSE.equals(r.testPlanProvided()), testNegated, testEvidence,
                    "원문에 테스트 미완료 표현");
            addMissingWithEvidence(missing, factors, corrections,
                    "rollbackPlan", "rollback_plan_missing", "rollbackPlanProvided",
                    Boolean.FALSE.equals(r.rollbackPlanProvided()), rollbackNegated, rollbackEvidence,
                    "원문에 롤백 계획 없음 표현");
        }
        // non-CHANGE는 아래 Rule에서 factors 전체를 비운다.

        // 5) Rule 3 — 유형·분류별 필수 항목 자동 보완 (AUTO_FILLABLE 안의 필드만)
        for (String field : requiredFieldsFor(requestType, category)) {
            if (!AUTO_FILLABLE.contains(field)) continue;      // 패턴 신뢰 어려운 필드는 AI 판단 유지
            if (missing.contains(field)) continue;
            if (isFieldProvided(field, r, text, requestedTime)) continue;
            missing.add(field);
            corrections.add("[FILL] missingField+" + field);
        }

        // 6) Rule 6 — 위험 요인은 CHANGE 전용. non-CHANGE는 factors·evidence를 비운다 ([NORM]).
        if (requestType != RequestType.CHANGE && !factors.isEmpty()) {
            corrections.add("[NORM] riskFactors cleared (requestType=" + requestType + " — CHANGE 전용)");
            factors.clear();
        }

        // 7) evidence는 최종 factors에 속한 것만 유지 (자동 추가된 factor는 evidence 없음 — corrections가 근거)
        List<Evidence> evidence = r.evidence() == null ? List.of()
                : r.evidence().stream()
                .filter(e -> e != null && factors.contains(e.factor()))
                .toList();

        // 7) 보완 질문 재생성
        List<String> questions = new ArrayList<>();
        for (String field : missing) {
            questions.add(TriageVocabulary.FIELD_QUESTIONS.get(field));
        }

        // 8) Rule 5 — 확신도 보정: [FIX]가 하나라도 있으면 0.7 미만으로 하향
        double confidence = r.confidence() == null ? 0.0 : Math.max(0.0, Math.min(1.0, r.confidence()));
        boolean hasFix = corrections.stream().anyMatch(c -> c.startsWith("[FIX]"));
        if (hasFix && confidence > 0.6) {
            corrections.add("[FIX] confidence→0.6 (AI 보정 발생 — 검토 필요)");
            confidence = 0.6;
        }

        return new TriageResult(
                requestType, category, r.environment(), requestedTime,
                r.recommendedPriority(), team, blankToNull(r.affectedService()),
                r.testPlanProvided(), r.rollbackPlanProvided(),
                List.copyOf(missing), List.copyOf(factors), evidence,
                questions, confidence,
                List.copyOf(corrections)
        );
    }

    /** AI FALSE면 [FILL], AI null/TRUE + 원문 부정이면 [FIX]로 분기해 missing/factor를 보완. */
    private static void addMissingWithEvidence(
            Set<String> missing, Set<String> factors, List<String> corrections,
            String missingKey, String factorKey, String providedFieldName,
            boolean aiFalse, boolean textNegated, boolean evidencePresent,
            String negatedReasonText) {
        if (evidencePresent) return;                 // 증거가 있으면 추가하지 않음
        if (!aiFalse && !textNegated) return;        // AI와 원문 어느 쪽도 "없음" 신호가 없음

        String prefix = aiFalse ? "[FILL]" : "[FIX]";
        String reason = aiFalse ? providedFieldName + "=false" : negatedReasonText;
        if (missing.add(missingKey)) {
            corrections.add(prefix + " missingField+" + missingKey + " (" + reason + ")");
        }
        if (factors.add(factorKey)) {
            corrections.add(prefix + " riskFactor+" + factorKey + " (" + reason + ")");
        }
    }

    private static List<String> requiredFieldsFor(RequestType type, String category) {
        if (type == RequestType.INCIDENT) {
            return TriageVocabulary.REQUIRED_BY_TYPE.getOrDefault(type, List.of());
        }
        return TriageVocabulary.REQUIRED_BY_CATEGORY.getOrDefault(category, List.of());
    }

    private static boolean isFieldProvided(String field, TriageResult r, String text, String effectiveRequestedTime) {
        return switch (field) {
            case "scheduledTime" -> effectiveRequestedTime != null;
            case "environment" -> r.environment() != null || ENV_WORDS.matcher(text).find();
            case "testPlan" -> (Boolean.TRUE.equals(r.testPlanProvided()) || TEST_PLAN_WORDS.matcher(text).find())
                    && !TEST_PLAN_NEGATED.matcher(text).find();
            case "rollbackPlan" -> (Boolean.TRUE.equals(r.rollbackPlanProvided()) || ROLLBACK_WORDS.matcher(text).find())
                    && !ROLLBACK_NEGATED.matcher(text).find();
            case "sourceIp", "destinationIp" -> IP_PATTERN.matcher(text).find();
            case "port" -> PORT_PATTERN.matcher(text).find();
            case "occurredAt" -> OCCURRED_WORDS.matcher(text).find() || !extractHours(text).isEmpty();
            case "errorMessage" -> ERROR_WORDS.matcher(text).find();
            default -> true;    // AUTO_FILLABLE 밖 필드는 호출되지 않지만, 호출돼도 "제공됨"으로 간주해 자동 추가 안 함
        };
    }

    /** 원문에서 시각(hour) 후보를 24시 기준 정수로 추출. "N시간"처럼 지속시간은 제외. */
    private static List<Integer> extractHours(String text) {
        List<Integer> hours = new ArrayList<>();
        Matcher m1 = HHMM.matcher(text);
        while (m1.find()) {
            int h = Integer.parseInt(m1.group(1));
            if (h >= 0 && h < 24) hours.add(h);
        }
        Matcher m2 = HOUR_WORD.matcher(text);
        while (m2.find()) {
            String ampm = m2.group(1);
            int h = Integer.parseInt(m2.group(2));
            if (h < 0 || h > 24) continue;
            if ("오후".equals(ampm) || "PM".equalsIgnoreCase(ampm)) {
                if (h != 12) h += 12;
            } else if ("오전".equals(ampm) || "AM".equalsIgnoreCase(ampm)) {
                if (h == 12) h = 0;
            }
            if (h >= 0 && h < 24) hours.add(h);
        }
        return hours;
    }

    private static Integer parseHour(String hhmm) {
        if (hhmm == null) return null;
        Matcher m = HHMM.matcher(hhmm);
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            return (h >= 0 && h < 24) ? h : null;
        }
        return null;
    }

    private static boolean isNightHour(String hhmm) {
        Integer h = parseHour(hhmm);
        return h != null && (h >= 22 || h < 6);
    }

    private static List<String> filter(List<String> values, Set<String> allowed) {
        if (values == null) return List.of();
        return values.stream().filter(allowed::contains).toList();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
