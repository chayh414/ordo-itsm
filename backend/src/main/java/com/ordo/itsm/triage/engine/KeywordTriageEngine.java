package com.ordo.itsm.triage.engine;

import com.ordo.itsm.ticket.EnvironmentType;
import com.ordo.itsm.ticket.Priority;
import com.ordo.itsm.ticket.RequestType;
import com.ordo.itsm.triage.TriageResult;
import com.ordo.itsm.triage.TriageResult.Evidence;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API 키 없이 동작하는 규칙 기반 트리아지.
 * LLM과 같은 출력 형식을 사용하므로 설정만 바꿔 교체할 수 있다.
 */
public class KeywordTriageEngine implements TriageEngine {

    private final JsonMapper jsonMapper;

    public KeywordTriageEngine(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String modelName() {
        return "keyword-rules-v1";
    }

    @Override
    public TriageOutcome analyze(String text) {
        boolean db = has(text, "DB|데이터베이스|테이블|컬럼|스키마|인덱스");
        boolean firewall = has(text, "방화벽|포트|ACL");
        boolean account = has(text, "계정|권한");
        boolean incident = has(text, "안\\s?됩니다|안\\s?돼|안\\s?되고|접속\\s?불가|장애|오류|에러|느려|먹통|다운");
        boolean inquiry = has(text, "문의|궁금|알려\\s?주");
        boolean production = has(text, "운영|프로덕션|실서버|(?i)production");

        String time = findTime(text);
        boolean night = has(text, "심야|새벽|야간") || isNight(time);

        RequestType type = incident ? RequestType.INCIDENT
                : (db || firewall) ? RequestType.CHANGE
                : account ? RequestType.SERVICE_REQUEST
                : inquiry ? RequestType.INQUIRY
                : RequestType.SERVICE_REQUEST;

        String category = switch (type) {
            case INCIDENT -> "SERVICE_OUTAGE";
            case CHANGE -> db ? "DATABASE_SCHEMA" : "FIREWALL";
            case SERVICE_REQUEST -> account ? "ACCOUNT" : "OTHER";
            case INQUIRY -> "GENERAL_INQUIRY";
        };

        String team = switch (category) {
            case "DATABASE_SCHEMA" -> "DB_OPERATION";
            case "FIREWALL" -> "NETWORK_SECURITY";
            case "SERVICE_OUTAGE" -> "APPLICATION_OPERATION";
            default -> "SERVICE_DESK";
        };

        Boolean testProvided = type == RequestType.CHANGE ? has(text, "테스트[^.\\n]{0,15}(완료|했|진행|검증)") : null;
        Boolean rollbackProvided = type == RequestType.CHANGE
                ? has(text, "롤백|원복") && !has(text, "(롤백|원복)[^.\\n]{0,20}(없|못|아직|미정|안\\s)")
                : null;

        String service = findService(text);

        List<String> missing = new ArrayList<>();
        List<String> factors = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();

        if (type == RequestType.CHANGE && db) {
            if (!production && !has(text, "개발|스테이징|테스트\\s?서버")) missing.add("environment");
            if (service == null) missing.add("affectedService");
            if (Boolean.FALSE.equals(testProvided)) missing.add("testPlan");
            if (Boolean.FALSE.equals(rollbackProvided)) missing.add("rollbackPlan");
            if (time == null) missing.add("scheduledTime");
            factors.add("database_schema_change");
            evidence.add(new Evidence("database_schema_change", snippet(text, "테이블|컬럼|스키마|인덱스|DB")));
        }
        if (type == RequestType.CHANGE && firewall) {
            if (!has(text, "\\d{1,3}(\\.\\d{1,3}){3}")) { missing.add("sourceIp"); missing.add("destinationIp"); }
            if (!has(text, "\\d{2,5}\\s?(번\\s?)?포트|포트\\s?\\d{2,5}")) missing.add("port");
            if (time == null) missing.add("scheduledTime");
            factors.add("firewall_change");
            evidence.add(new Evidence("firewall_change", snippet(text, "방화벽|포트")));
        }
        if (type == RequestType.INCIDENT) {
            if (service == null) missing.add("affectedService");
            if (!has(text, "\\d{1,2}\\s?시|\\d{1,2}:\\d{2}|부터|아침|오전|오후")) missing.add("occurredAt");
            if (!has(text, "오류\\s?메시지|에러\\s?코드|\\d{3}\\s?에러")) missing.add("errorMessage");
        }
        if (type == RequestType.CHANGE) {
            if (production) {
                factors.add("production_environment");
                evidence.add(new Evidence("production_environment", snippet(text, "운영|프로덕션|실서버")));
            }
            if (Boolean.FALSE.equals(rollbackProvided)) {
                factors.add("rollback_plan_missing");
                evidence.add(new Evidence("rollback_plan_missing", snippet(text, "롤백|원복")));
            }
            if (Boolean.FALSE.equals(testProvided)) {
                factors.add("test_plan_missing");
            }
            if (night) {
                factors.add("night_work");
                evidence.add(new Evidence("night_work", time != null ? time : snippet(text, "심야|새벽|야간")));
            }
        }

        Priority priority = switch (type) {
            case INCIDENT -> has(text, "전체|모든|전사|전원") ? Priority.P2 : Priority.P3;
            case INQUIRY -> Priority.P4;
            default -> Priority.P3;
        };

        TriageResult result = new TriageResult(
                type, category,
                production ? EnvironmentType.PRODUCTION : null,
                time, priority, team, service,
                testProvided, rollbackProvided,
                missing, factors, evidence,
                List.of(),   // 질문은 서버 검증 단계에서 누락 항목 기준으로 생성
                0.6,         // 규칙 기반이므로 낮은 확신도 → 운영 담당자 검토 필요
                null         // corrections — TriageValidator가 채운다
        );
        return new TriageOutcome(jsonMapper.writeValueAsString(result), result);
    }

    private static boolean has(String text, String regex) {
        return Pattern.compile(regex).matcher(text).find();
    }

    private static String findTime(String text) {
        Matcher hm = Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(text);
        if (hm.find()) {
            return String.format("%02d:%s", Integer.parseInt(hm.group(1)), hm.group(2));
        }
        Matcher h = Pattern.compile("(\\d{1,2})\\s?시").matcher(text);
        if (h.find()) {
            return String.format("%02d:00", Integer.parseInt(h.group(1)));
        }
        return null;
    }

    private static boolean isNight(String hhmm) {
        if (hhmm == null) return false;
        int hour = Integer.parseInt(hhmm.substring(0, 2));
        return hour >= 22 || hour < 6;
    }

    private static String findService(String text) {
        Matcher m = Pattern.compile("([가-힣A-Za-z]+(?: [가-힣A-Za-z]+)?)\\s*(?:테이블|서비스|시스템|서버)").matcher(text);
        if (m.find() && !m.group(1).matches("운영|DB|개발")) {
            return m.group(1);
        }
        Matcher acronym = Pattern.compile("\\b(?!DB\\b)([A-Z]{2,})\\b").matcher(text);
        return acronym.find() ? acronym.group(1) : null;
    }

    private static String snippet(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (!m.find()) return "";
        int start = Math.max(0, m.start() - 10);
        int end = Math.min(text.length(), m.end() + 15);
        return text.substring(start, end).trim();
    }
}
