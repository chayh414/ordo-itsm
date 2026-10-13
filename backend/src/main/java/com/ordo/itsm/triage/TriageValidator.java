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
    /** "N시" — 뒤에 "간"이 붙으면(시간, 시간대) 매치 제외. 선택 수식어: 오전/오후/새벽/아침/저녁/밤/AM/PM. */
    private static final Pattern HOUR_WORD = Pattern.compile(
            "(오전|오후|새벽|아침|저녁|밤|AM|PM|am|pm)?\\s*(\\d{1,2})\\s*시(?!간)");
    private static final Pattern NIGHT_WORDS = Pattern.compile("심야|새벽|야간|밤");
    // \b 는 테스트 환경에 따라 유니코드 모드가 켜지면 다르게 작동할 수 있어
    // 명시적 lookaround 로 "앞뒤가 숫자/점이 아닌" 경계를 지정한다.
    private static final Pattern IP_PATTERN = Pattern.compile("(?<![\\d.])\\d{1,3}(?:\\.\\d{1,3}){3}(?![\\d.])");
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
        return validate(r, rawText, Map.of());
    }

    /**
     * supplements: 고객이 "확인이 필요한 내용" 질문에 답한 필드 → 답변 내용.
     * 원문에 부정 표현(예: "롤백 없어요")이 남아 있어도 보완된 필드는 "제공됨"으로 간주해
     * 해당 missing·위험 요인을 제거한다. 보정 로그는 [SUP] 프리픽스 (확신도 하향 대상 아님).
     */
    public TriageResult validate(TriageResult r, String rawText, Map<String, String> supplements) {
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

        // 3) Rule 2 — 시각 환각 차단/교정 (모호한 수식어 없는 1~11시는 두 후보로 처리)
        List<HourExpr> hourExprs = extractHourExprs(text);
        String requestedTime = r.requestedTime();
        if (requestedTime != null) {
            Integer aiHour = parseHour(requestedTime);
            if (hourExprs.isEmpty()) {
                corrections.add("[FIX] requestedTime=" + requestedTime + "→null (원문에 시각 근거 없음)");
                requestedTime = null;
            } else if (aiHour != null && hourExprs.stream().anyMatch(e -> e.candidates().contains(aiHour))) {
                // AI 시가 어느 후보와 일치 → 유지 (모호한 경우도 AI가 선택한 쪽이 유효한 해석 중 하나)
            } else {
                Integer firstUnambiguous = hourExprs.stream()
                        .filter(e -> !e.ambiguous())
                        .flatMap(e -> e.candidates().stream())
                        .findFirst()
                        .orElse(null);
                if (firstUnambiguous != null) {
                    String corrected = String.format("%02d:00", firstUnambiguous);
                    corrections.add("[FIX] requestedTime: " + requestedTime + "→" + corrected + " (원문 기준)");
                    requestedTime = corrected;
                } else {
                    // 모든 후보가 모호 → 어느 쪽으로도 단정하지 않고 null 처리 (scheduledTime은 Rule 3에서 missing에 추가됨)
                    corrections.add("[FIX] requestedTime=" + requestedTime + "→null (원문 시각이 모호함)");
                    requestedTime = null;
                }
            }
        }

        // night_work — 심야 단어 OR 확정 시각이 야간대일 때만 "근거 있음".
        // 모호한 시각(1~11시 수식어 없음)은 그 자체로는 근거로 쓰지 않음 (추가/제거 모두 안 함).
        if (requestType == RequestType.CHANGE) {
            boolean nightWord = NIGHT_WORDS.matcher(text).find();
            Set<Integer> unambiguousHours = new LinkedHashSet<>();
            for (HourExpr e : hourExprs) {
                if (!e.ambiguous()) unambiguousHours.addAll(e.candidates());
            }
            boolean confirmedNight = nightWord || unambiguousHours.stream().anyMatch(h -> h >= 22 || h < 6);
            boolean confirmedNotNight = !confirmedNight && !unambiguousHours.isEmpty();
            boolean hasNightFactor = factors.contains("night_work");
            if (confirmedNight && !hasNightFactor) {
                factors.add("night_work");
                corrections.add("[FIX] riskFactor+night_work (심야 작업 근거 있음)");
            } else if (confirmedNotNight && hasNightFactor) {
                factors.remove("night_work");
                corrections.add("[FIX] riskFactor-night_work (근거 없음)");
            }
            // 모호한 경우(unambiguousHours 비어있고 심야 단어 없음) → 추가/제거 모두 skip
        }

        // 4) rollback/test 동기화 (CHANGE 전체) :
        //    - missing(rollbackPlan/testPlan) 과 factor(rollback_plan_missing/test_plan_missing) 는 한 곳에서 같이 결정.
        //    - 언급 판정은 기존 원문 로직(ROLLBACK_WORDS/TEST_PLAN_WORDS + NEGATED, AI *Provided 플래그).
        //    - supplements 에 값이 들어있으면 "언급 있음" 으로 우선 인정 (고객 보완은 원문 부정보다 우선).
        //    - 언급 있음 → 둘 다 제거, 없음 → 둘 다 추가. non-CHANGE 는 아래 Rule 6 에서 factors 가 비워진다.
        if (requestType == RequestType.CHANGE) {
            boolean testSupplied = hasSupplementValue(supplements, "testPlan");
            boolean rollbackSupplied = hasSupplementValue(supplements, "rollbackPlan");

            boolean testNegated = TEST_PLAN_NEGATED.matcher(text).find();
            boolean rollbackNegated = ROLLBACK_NEGATED.matcher(text).find();

            boolean testTextEvidence = (Boolean.TRUE.equals(r.testPlanProvided())
                    || TEST_PLAN_WORDS.matcher(text).find()) && !testNegated;
            boolean rollbackTextEvidence = (Boolean.TRUE.equals(r.rollbackPlanProvided())
                    || ROLLBACK_WORDS.matcher(text).find()) && !rollbackNegated;

            boolean testEvidence = testTextEvidence || testSupplied;
            boolean rollbackEvidence = rollbackTextEvidence || rollbackSupplied;

            syncChangeMissingFactor(missing, factors, corrections,
                    "testPlan", "test_plan_missing", testEvidence, testSupplied,
                    r.testPlanProvided(), testNegated);
            syncChangeMissingFactor(missing, factors, corrections,
                    "rollbackPlan", "rollback_plan_missing", rollbackEvidence, rollbackSupplied,
                    r.rollbackPlanProvided(), rollbackNegated);
        }

        // 5) Rule 3 — 유형·분류별 필수 항목 자동 보완 (AUTO_FILLABLE 안의 필드만)
        for (String field : requiredFieldsFor(requestType, category)) {
            if (!AUTO_FILLABLE.contains(field)) continue;      // 패턴 신뢰 어려운 필드는 AI 판단 유지
            // testPlan/rollbackPlan 은 위 Rule 4 에서 CHANGE 전체에 대해 이미 결정됨 — 중복 처리 금지.
            if ("testPlan".equals(field) || "rollbackPlan".equals(field)) continue;
            if (missing.contains(field)) continue;
            if (isFieldProvided(field, r, text, requestedTime)) continue;
            missing.add(field);
            corrections.add("[FILL] missingField+" + field);
        }

        // 5.5) 방화벽·네트워크: 원문에 IP 가 2개 이상이면 source/destination IP 는 "제공됨"으로 간주.
        //      AI 가 missing 에 넣었어도 제거. (한 쪽만 있으면 어느 쪽인지 불분명 — missing 유지)
        {
            int ipCount = 0;
            Matcher ipMatcher = IP_PATTERN.matcher(text);
            while (ipMatcher.find()) ipCount++;
            if (ipCount >= 2) {
                boolean removedSrc = missing.remove("sourceIp");
                boolean removedDst = missing.remove("destinationIp");
                if (removedSrc || removedDst) {
                    corrections.add("[FIX] sourceIp·destinationIp missing 해소 (원문에 IP " + ipCount + "개)");
                }
            }
        }

        // 6) Rule 6 — 위험 요인은 CHANGE 전용. non-CHANGE는 factors·evidence를 비운다 ([NORM]).
        if (requestType != RequestType.CHANGE && !factors.isEmpty()) {
            corrections.add("[NORM] riskFactors cleared (requestType=" + requestType + " — CHANGE 전용)");
            factors.clear();
        }

        // 6.5) 고객 보완(supplements) 반영 — testPlan/rollbackPlan 은 위 Rule 4 에서 이미 처리.
        //      그 외 필드는 missing 에서만 제거 (대응 factor 없음).
        if (supplements != null && !supplements.isEmpty()) {
            for (String field : supplements.keySet()) {
                if ("testPlan".equals(field) || "rollbackPlan".equals(field)) continue;
                if (missing.remove(field)) {
                    corrections.add("[SUP] " + field + " 고객 보완으로 missing 해소");
                }
            }
        }

        // 7) evidence 정리.
        //    각 factor 마다 서버가 원문에서 패턴으로 "그 요인 절" 만 추출 (사용자가 원하는 짧은 근거).
        //    서버 패턴이 못 찾으면 AI 가 보낸 quote 를 fallback 으로 사용 (지어내지 않음).
        java.util.LinkedHashMap<String, Evidence> byFactor = new java.util.LinkedHashMap<>();
        for (String f : factors) {
            String snippet = findEvidenceSnippet(f, text);
            if (!snippet.isBlank()) {
                byFactor.put(f, new Evidence(f, snippet));
            } else if (r.evidence() != null) {
                for (Evidence e : r.evidence()) {
                    if (e != null && f.equals(e.factor()) && e.quote() != null && !e.quote().isBlank()) {
                        byFactor.put(f, e);
                        break;
                    }
                }
            }
        }
        List<Evidence> evidence = factors.stream()
                .map(byFactor::get)
                .filter(e -> e != null)
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

        // 9) title 폴백 — AI title 에 작업 동사 없거나 20자 초과면 원문의 작업구로 재구성.
        String title = resolveTitle(r.title(), blankToNull(r.affectedService()), category, text);

        return new TriageResult(
                requestType, category, title,
                r.environment(), requestedTime,
                r.recommendedPriority(), team, blankToNull(r.affectedService()),
                r.testPlanProvided(), r.rollbackPlanProvided(),
                List.copyOf(missing), List.copyOf(factors), evidence,
                questions, confidence,
                List.copyOf(corrections)
        );
    }

    /** title 에 들어가야 할 작업 동사들. 하나라도 포함돼야 "무엇을 하는지" 가 명확. */
    private static final Pattern TITLE_VERBS = Pattern.compile(
            "추가|변경|삭제|제거|오픈|열기|닫기|생성|부여|회수|수정|적용|복구|점검|이관|배포|승인|이전|확인|조회|신청|신고|해제|차단|허용|전환|재시작|재부팅|증설|축소|이동|권한");

    /** 원문에서 "N{작업동사}" 패턴의 작업구를 뽑는다 (title 폴백용). 예: "컬럼 추가", "포트 오픈", "권한 부여". */
    private static final Pattern WORK_PHRASE = Pattern.compile(
            "(\\S{1,8}\\s?(?:추가|변경|삭제|제거|오픈|열기|닫기|생성|부여|회수|수정|적용|복구|점검|이관|배포|승인|이전|확인|조회|신청|신고|해제|차단|허용|전환|재시작|재부팅|증설|축소))");

    /**
     * title 폴백:
     *   1) AI 가 준 title 이 20자 이내 + 작업 동사 포함 → 그대로 사용
     *   2) 그 외: 원문에서 작업구(예 "컬럼 추가") 추출 → "{service} {작업구}"
     *   3) 작업구도 못 찾으면 "{service} {category}" → category → "요청" 순 폴백
     */
    private static String resolveTitle(String aiTitle, String service, String category, String text) {
        if (aiTitle != null) {
            String t = aiTitle.trim();
            if (!t.isEmpty() && t.length() <= 20 && TITLE_VERBS.matcher(t).find()) {
                return t;
            }
        }
        // 원문에서 작업구 추출
        if (text != null && !text.isBlank()) {
            Matcher m = WORK_PHRASE.matcher(text);
            if (m.find()) {
                String phrase = m.group(1).replaceAll("\\s+", " ").trim();
                if (service != null && !service.isBlank()) {
                    String combo = (service.trim() + " " + phrase).trim();
                    return combo.length() > 20 ? combo.substring(0, 20).trim() : combo;
                }
                return phrase.length() > 20 ? phrase.substring(0, 20).trim() : phrase;
            }
        }
        // 작업구 없음 → service + category
        String catKo = TriageLabels.category(category);
        if (service != null && !service.isBlank() && catKo != null && !catKo.isBlank()) {
            String combo = service.trim() + " " + catKo;
            return combo.length() > 20 ? combo.substring(0, 20) : combo;
        }
        if (service != null && !service.isBlank()) return service.trim();
        return catKo == null || catKo.isBlank() ? "요청" : catKo;
    }

    /**
     * factor → 원문 근거 스니펫. 각 요인을 "말하는 절(clause)" 만 매치한다.
     *   - rollback/test_plan_missing: "롤백 … 없/못/아직" 식으로 부정 어구까지 포함해 끝.
     *   - night_work: 시각/야간 표현 자체만 (뒤의 작업 대상 등은 다른 주제).
     * 매치 자체가 이미 "절 단위" 로 끝나므로 findEvidenceSnippet 에서 어절 확장이 거의 필요 없다.
     */
    private static final Map<String, Pattern> FACTOR_EVIDENCE_PATTERNS;
    static {
        Map<String, Pattern> m = new java.util.LinkedHashMap<>();
        m.put("production_environment", Pattern.compile("운영\\s?(?:DB|서버|환경)?|프로덕션|실서버|실서비스"));
        m.put("database_schema_change", Pattern.compile("테이블|컬럼|스키마|인덱스"));
        m.put("database_permission_change", Pattern.compile("권한|GRANT|REVOKE", Pattern.CASE_INSENSITIVE));
        m.put("firewall_change", Pattern.compile("방화벽|포트|ACL", Pattern.CASE_INSENSITIVE));
        m.put("network_change", Pattern.compile("네트워크|라우팅|VLAN|라우터", Pattern.CASE_INSENSITIVE));
        // 롤백/테스트 주제어부터 다음 문장 경계(. ! ? 줄바꿈)까지. 20자 캡으로 짧게 자른다.
        // 요인은 이미 결정된 상태에서 evidence 를 뽑으므로 "부정 어구" 체크를 여기서 다시 하지 않는다.
        m.put("rollback_plan_missing", Pattern.compile("(?:롤백|원복)[^.!?\\n]*"));
        m.put("test_plan_missing", Pattern.compile("테스트[^.!?\\n]*"));
        // 심야: "오늘 23시", "밤 2시", "새벽" 등. 시간 phrase 자체만.
        m.put("night_work", Pattern.compile(
                "(?:오늘|내일|모레|어제)?\\s*(?:오전|오후|새벽|아침|저녁|밤|심야|야간|AM|PM)\\s*\\d{1,2}\\s*시"
                        + "|(?:오늘|내일|모레|어제)?\\s*\\d{1,2}\\s*시"
                        + "|심야|새벽|야간"));
        FACTOR_EVIDENCE_PATTERNS = Map.copyOf(m);
    }

    /**
     * 원문에서 요인 근거 스니펫을 뽑는다.
     *   - rollback/test/night: 패턴 자체가 "절 단위" 로 끝나므로 **매치 그대로 사용** (어절 확장 금지).
     *   - 그 외: 단어 매치라 어절 2개까지 확장해 자연스러운 구절 생성.
     * 조사/연결어로 시작하지 않도록 어절 경계로 왼쪽을 자른다. 20자 캡.
     */
    private static final Set<String> CLAUSE_SCOPED_FACTORS = Set.of(
            "rollback_plan_missing", "test_plan_missing", "night_work");

    private static String findEvidenceSnippet(String factor, String text) {
        if (text == null || text.isBlank()) return "";
        Pattern p = FACTOR_EVIDENCE_PATTERNS.get(factor);
        if (p == null) return "";
        Matcher m = p.matcher(text);
        if (!m.find()) return "";

        // 왼쪽 경계: 매치 지점이 속한 어절의 시작까지 (공백/구두점을 만나면 거기서 멈춤)
        int s = m.start();
        while (s > 0 && !isWordBoundary(text.charAt(s - 1))) s--;

        int e = m.end();
        if (!CLAUSE_SCOPED_FACTORS.contains(factor)) {
            // 매치 이후 어절 2개까지 늘림 (너무 길어지지 않게)
            int extraWords = 0;
            boolean inWord = (e < text.length() && !isWordBoundary(text.charAt(e)));
            while (e < text.length() && extraWords < 2) {
                char c = text.charAt(e);
                if (isWordBoundary(c)) {
                    if (inWord) extraWords++;
                    inWord = false;
                } else {
                    inWord = true;
                }
                e++;
            }
            // 어절 중간에서 끝났으면 그 어절 끝까지 포함
            while (e < text.length() && !isWordBoundary(text.charAt(e))) e++;
        }

        String snippet = text.substring(s, e).replaceAll("[\\n\\r]+", " ").trim();
        if (snippet.length() > 20) {
            snippet = snippet.substring(0, 20);
            int lastSpace = snippet.lastIndexOf(' ');
            if (lastSpace > 5) snippet = snippet.substring(0, lastSpace).trim();
        }
        return snippet;
    }

    private static boolean isWordBoundary(char c) {
        return Character.isWhitespace(c) || ".,;!?()[]{}:\"'".indexOf(c) >= 0;
    }

    /**
     * CHANGE 요청에서 testPlan/rollbackPlan 의 missing·factor 를 한 곳에서 결정.
     *   - evidence=true  : missing·factor 둘 다 제거 (supplements 로 해소되면 [SUP], 원문/AI 근거면 [FIX])
     *   - evidence=false, AI 가 Provided=TRUE 라고 알렸는데 원문엔 부정 표현 → [FIX] (AI 거짓 보고, 확신도 하향 트리거)
     *   - evidence=false, 그 외 (AI 가 언급 안 했거나 자인) → [FILL]
     *   어느 경로든 missing·factor 가 "따로 놀" 수 없다.
     */
    private static void syncChangeMissingFactor(
            Set<String> missing, Set<String> factors, List<String> corrections,
            String missingKey, String factorKey, boolean evidence, boolean supplied,
            Boolean aiProvided, boolean textNegated) {
        if (evidence) {
            boolean removedMissing = missing.remove(missingKey);
            boolean removedFactor = factors.remove(factorKey);
            if (removedMissing || removedFactor) {
                String prefix = supplied ? "[SUP]" : "[FIX]";
                String reason = supplied ? "고객 보완으로 해소" : "원문/AI 근거로 해소";
                corrections.add(prefix + " " + missingKey + "·" + factorKey + " 해소 (" + reason + ")");
            }
        } else {
            boolean addedMissing = missing.add(missingKey);
            boolean addedFactor = factors.add(factorKey);
            if (addedMissing || addedFactor) {
                // AI 가 TRUE 라고 알렸는데 원문이 부정 → [FIX] (hasFix → 확신도 하향 → needsReview)
                boolean aiFalseReport = Boolean.TRUE.equals(aiProvided) && textNegated;
                String prefix = aiFalseReport ? "[FIX]" : "[FILL]";
                String reason = aiFalseReport
                        ? "AI 는 제공됨으로 보고, 원문에 테스트 미완료" + (missingKey.equals("rollbackPlan") ? " 또는 롤백 계획 없음" : "")
                        : "CHANGE 요청에 명시 없음";
                corrections.add(prefix + " " + missingKey + "·" + factorKey + " 보강 (" + reason + ")");
            }
        }
    }

    private static boolean hasSupplementValue(Map<String, String> supplements, String field) {
        if (supplements == null) return false;
        String v = supplements.get(field);
        return v != null && !v.isBlank();
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
            case "occurredAt" -> OCCURRED_WORDS.matcher(text).find() || !extractHourExprs(text).isEmpty();
            case "errorMessage" -> ERROR_WORDS.matcher(text).find();
            default -> true;    // AUTO_FILLABLE 밖 필드는 호출되지 않지만, 호출돼도 "제공됨"으로 간주해 자동 추가 안 함
        };
    }

    /**
     * 원문에서 시각 표현을 뽑아 각 occurrence마다 후보 집합과 모호성 플래그를 돌려준다.
     *   - HH:MM, "0시", "12시", "13~23시" → 확정 (ambiguous=false)
     *   - 수식어(오전/오후/새벽/아침/저녁/밤/AM/PM) + "N시" → 확정
     *   - 수식어 없는 "1~11시" → 모호 (후보 {N, N+12})
     *   - "N시간"(지속시간)은 제외
     */
    private record HourExpr(Set<Integer> candidates, boolean ambiguous) {
    }

    private static List<HourExpr> extractHourExprs(String text) {
        List<HourExpr> exprs = new ArrayList<>();
        Matcher m1 = HHMM.matcher(text);
        while (m1.find()) {
            int h = Integer.parseInt(m1.group(1));
            if (h >= 0 && h < 24) exprs.add(new HourExpr(Set.of(h), false));
        }
        Matcher m2 = HOUR_WORD.matcher(text);
        while (m2.find()) {
            String modifier = m2.group(1);
            int h = Integer.parseInt(m2.group(2));
            if (h < 0 || h > 24) continue;

            if (modifier == null) {
                // 수식어 없음 — 1~11시만 모호, 그 외(0, 12, 13~23)는 24시간 표기로 간주
                if (h >= 1 && h <= 11) {
                    exprs.add(new HourExpr(Set.of(h, h + 12), true));
                } else if (h >= 0 && h < 24) {
                    exprs.add(new HourExpr(Set.of(h), false));
                }
            } else {
                Integer converted = convertWithModifier(modifier, h);
                if (converted != null) {
                    exprs.add(new HourExpr(Set.of(converted), false));
                }
            }
        }
        return exprs;
    }

    /**
     * 한국어 시각 수식어 + N시(0~24) → 24시 표기로 변환.
     *   "밤 N시"   : N=1~5 → N (새벽대), N=6~11 → N+12 (저녁~늦은 밤), N=12 → 0 (자정)
     *   "저녁/오후": N=1~11 → N+12, N=12 → 12
     *   "새벽/오전/아침": N=12 → 0, 그 외 → N
     * 비정상 입력(h≥13 등)은 그대로 유지해 손상시키지 않음.
     */
    private static Integer convertWithModifier(String modifier, int h) {
        if ("밤".equals(modifier)) {
            if (h >= 1 && h <= 5) return h;
            if (h >= 6 && h <= 11) return h + 12;
            if (h == 12) return 0;
            return (h >= 0 && h < 24) ? h : null;
        }
        if (isPmModifier(modifier)) {
            if (h >= 1 && h <= 11) return h + 12;
            return (h >= 0 && h < 24) ? h : null;
        }
        if (isAmModifier(modifier)) {
            if (h == 12) return 0;
            return (h >= 0 && h < 24) ? h : null;
        }
        return null;
    }

    private static boolean isAmModifier(String m) {
        return "오전".equals(m) || "새벽".equals(m) || "아침".equals(m) || "AM".equalsIgnoreCase(m);
    }

    /** 순수 PM 수식어 (오후/저녁/PM). "밤"은 N 범위에 따라 갈라져 별도 처리. */
    private static boolean isPmModifier(String m) {
        return "오후".equals(m) || "저녁".equals(m) || "PM".equalsIgnoreCase(m);
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

    private static List<String> filter(List<String> values, Set<String> allowed) {
        if (values == null) return List.of();
        return values.stream().filter(allowed::contains).toList();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
