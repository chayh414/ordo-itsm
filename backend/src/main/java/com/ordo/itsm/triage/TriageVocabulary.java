package com.ordo.itsm.triage;

import com.ordo.itsm.ticket.RequestType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI가 사용할 수 있는 값의 목록. 서버는 이 목록 밖의 값을 버린다. */
public final class TriageVocabulary {

    private TriageVocabulary() {
    }

    /** 유형별 필수 항목 (INCIDENT 전용 — CHANGE/REQUEST는 카테고리 기준) */
    public static final Map<RequestType, List<String>> REQUIRED_BY_TYPE = Map.of(
            RequestType.INCIDENT, List.of("affectedService", "occurredAt", "impactScope", "errorMessage")
    );

    /** 분류별 필수 항목. TriageValidator가 결과·원문에 근거가 없는 항목만 missingFields에 추가한다. */
    public static final Map<String, List<String>> REQUIRED_BY_CATEGORY;

    static {
        Map<String, List<String>> req = new LinkedHashMap<>();
        req.put("FIREWALL", List.of("sourceIp", "destinationIp", "port", "protocol", "scheduledTime", "reason"));
        req.put("NETWORK", List.of("sourceIp", "destinationIp", "port", "protocol", "scheduledTime", "reason"));
        req.put("DATABASE_SCHEMA", List.of("targetDatabase", "changeDescription", "environment",
                "affectedService", "testPlan", "rollbackPlan", "scheduledTime"));
        req.put("DATABASE_PERMISSION", List.of("targetDatabase", "requiredPermission", "scheduledTime", "reason"));
        req.put("DATABASE_PATCH", List.of("targetDatabase", "scheduledTime", "rollbackPlan", "testPlan"));
        req.put("ACCOUNT", List.of("targetSystem", "targetAccount", "requiredPermission", "reason", "usagePeriod"));
        REQUIRED_BY_CATEGORY = Map.copyOf(req);
    }

    public static final List<String> CATEGORIES = List.of(
            "DATABASE_SCHEMA", "DATABASE_PERMISSION", "DATABASE_PATCH",
            "FIREWALL", "NETWORK", "SERVER", "ACCOUNT", "APPLICATION",
            "SERVICE_OUTAGE", "PERFORMANCE", "GENERAL_INQUIRY", "OTHER"
    );

    public static final List<String> TEAMS = List.of(
            "DB_OPERATION", "NETWORK_SECURITY", "SYSTEM_OPERATION", "APPLICATION_OPERATION", "SERVICE_DESK"
    );

    public static final List<String> RISK_FACTORS = List.of(
            "production_environment",
            "database_schema_change",
            "database_permission_change",
            "firewall_change",
            "network_change",
            "rollback_plan_missing",
            "test_plan_missing",
            "night_work"
    );

    /** 누락 항목 키 → 고객에게 보여줄 보완 질문 */
    public static final Map<String, String> FIELD_QUESTIONS;

    static {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("affectedService", "영향을 받는 서비스(시스템) 이름을 알려주세요.");
        q.put("occurredAt", "문제가 처음 발생한 시각을 알려주세요.");
        q.put("impactScope", "영향 범위(사용자 수, 부서 등)를 알려주세요.");
        q.put("errorMessage", "화면에 표시된 오류 메시지나 증상을 알려주세요.");
        q.put("sourceIp", "출발지 IP를 알려주세요.");
        q.put("destinationIp", "목적지 IP를 알려주세요.");
        q.put("port", "오픈할 포트 번호를 알려주세요.");
        q.put("protocol", "프로토콜(TCP/UDP)을 알려주세요.");
        q.put("scheduledTime", "작업 희망 일시를 알려주세요.");
        q.put("reason", "요청 사유를 알려주세요.");
        q.put("targetDatabase", "대상 DB(인스턴스·스키마) 이름을 알려주세요.");
        q.put("changeDescription", "변경할 내용을 구체적으로 알려주세요.");
        q.put("environment", "대상 환경(운영/스테이징/개발)을 알려주세요.");
        q.put("testPlan", "테스트 계획 또는 테스트 결과를 알려주세요.");
        q.put("rollbackPlan", "작업 실패 시 원복(롤백) 절차를 알려주세요.");
        q.put("targetSystem", "대상 시스템을 알려주세요.");
        q.put("targetAccount", "대상 계정을 알려주세요.");
        q.put("requiredPermission", "필요한 권한을 알려주세요.");
        q.put("usagePeriod", "권한 사용 기간을 알려주세요.");
        FIELD_QUESTIONS = Map.copyOf(q);
    }

    public static List<String> fieldKeys() {
        return List.copyOf(FIELD_QUESTIONS.keySet());
    }
}
