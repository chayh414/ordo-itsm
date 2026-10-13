package com.ordo.itsm.triage;

import java.util.Map;

/**
 * 백엔드가 생성하는 사용자용 문자열(도구 llmText 등)에서 영어 enum 코드를 한글로 바꾸는 매퍼.
 * - LLM 이 tool 결과를 보고 답변을 작성할 때 한글이면 그대로 한글로 받아쓰기 쉬움
 * - 프론트의 i18n 매핑과 짝을 이룬다 (프론트는 UI 렌더링용, 여기는 LLM 입력용)
 *
 * 매핑에 없는 값은 원문 그대로 돌려줘서 enum 이 확장돼도 깨지지 않음.
 */
public final class TriageLabels {

    private TriageLabels() {}

    private static final Map<String, String> REQUEST_TYPE = Map.of(
            "INCIDENT", "장애",
            "SERVICE_REQUEST", "일반 요청",
            "CHANGE", "변경 요청",
            "INQUIRY", "문의"
    );

    private static final Map<String, String> CATEGORY = Map.ofEntries(
            Map.entry("DATABASE_SCHEMA", "DB 스키마"),
            Map.entry("DATABASE_PERMISSION", "DB 권한"),
            Map.entry("DATABASE_PATCH", "DB 패치"),
            Map.entry("FIREWALL", "방화벽"),
            Map.entry("NETWORK", "네트워크"),
            Map.entry("SERVER", "서버"),
            Map.entry("ACCOUNT", "계정"),
            Map.entry("APPLICATION", "애플리케이션"),
            Map.entry("SERVICE_OUTAGE", "서비스 장애"),
            Map.entry("PERFORMANCE", "성능"),
            Map.entry("GENERAL_INQUIRY", "일반 문의"),
            Map.entry("OTHER", "기타")
    );

    private static final Map<String, String> STATUS = Map.ofEntries(
            Map.entry("DRAFT", "요청서 초안"),
            Map.entry("SUBMITTED", "접수됨"),
            Map.entry("TRIAGED", "분류 완료"),
            Map.entry("INFO_REQUIRED", "보완 대기"),
            Map.entry("RISK_REVIEWED", "위험도 검토 완료"),
            Map.entry("PENDING_APPROVAL", "승인 대기"),
            Map.entry("APPROVED", "승인됨"),
            Map.entry("REJECTED", "반려됨"),
            Map.entry("ASSIGNED", "담당자 배정"),
            Map.entry("SCHEDULED", "작업 예정"),
            Map.entry("IN_PROGRESS", "처리 중"),
            Map.entry("RESOLVED", "해결됨"),
            Map.entry("COMPLETED", "완료"),
            Map.entry("VERIFIED", "검증 완료"),
            Map.entry("CLOSED", "종료"),
            Map.entry("CANCELLED", "취소됨")
    );

    private static final Map<String, String> ENVIRONMENT = Map.of(
            "PRODUCTION", "운영",
            "STAGING", "스테이징",
            "DEV", "개발"
    );

    private static final Map<String, String> RISK_FACTOR = Map.of(
            "production_environment", "운영 환경",
            "database_schema_change", "DB 스키마 변경",
            "database_permission_change", "DB 권한 변경",
            "firewall_change", "방화벽 변경",
            "network_change", "네트워크 변경",
            "rollback_plan_missing", "롤백 계획 없음",
            "test_plan_missing", "테스트 계획 없음",
            "night_work", "심야 작업"
    );

    private static final Map<String, String> FIELD = Map.ofEntries(
            Map.entry("affectedService", "대상 서비스"),
            Map.entry("occurredAt", "발생 시각"),
            Map.entry("impactScope", "영향 범위"),
            Map.entry("errorMessage", "오류 메시지"),
            Map.entry("sourceIp", "출발지 IP"),
            Map.entry("destinationIp", "목적지 IP"),
            Map.entry("port", "포트"),
            Map.entry("protocol", "프로토콜"),
            Map.entry("scheduledTime", "작업 희망 일시"),
            Map.entry("reason", "요청 사유"),
            Map.entry("targetDatabase", "대상 DB"),
            Map.entry("changeDescription", "변경 내용"),
            Map.entry("environment", "대상 환경"),
            Map.entry("testPlan", "테스트 계획"),
            Map.entry("rollbackPlan", "롤백 절차"),
            Map.entry("targetSystem", "대상 시스템"),
            Map.entry("targetAccount", "대상 계정"),
            Map.entry("requiredPermission", "필요 권한"),
            Map.entry("usagePeriod", "사용 기간")
    );

    public static String requestType(String v) { return v == null ? "요청" : REQUEST_TYPE.getOrDefault(v, v); }
    public static String category(String v) { return v == null ? "" : CATEGORY.getOrDefault(v, v); }
    public static String status(String v) { return v == null ? "" : STATUS.getOrDefault(v, v); }
    public static String environment(String v) { return v == null ? "" : ENVIRONMENT.getOrDefault(v, v); }
    public static String riskFactor(String v) { return v == null ? "" : RISK_FACTOR.getOrDefault(v, v); }
    public static String field(String v) { return v == null ? "" : FIELD.getOrDefault(v, v); }

    /**
     * AssistantService 가 reply 치환에 쓰는 전체 enum→한글 매핑.
     * 영어 enum 코드가 LLM 답변에 섞여 나오는 상황을 안전차로 가린다.
     */
    public static Map<String, String> allEnumMappings() {
        Map<String, String> all = new java.util.LinkedHashMap<>();
        all.putAll(STATUS);         // INFO_REQUIRED → 보완 대기 등 (상태값이 최우선 — 사용자 혼란 가장 큰 포인트)
        all.putAll(CATEGORY);       // DATABASE_SCHEMA → DB 스키마
        all.putAll(REQUEST_TYPE);   // CHANGE → 변경 요청
        all.putAll(ENVIRONMENT);    // PRODUCTION → 운영
        all.putAll(RISK_FACTOR);    // night_work → 심야 작업
        all.putAll(FIELD);          // rollbackPlan → 롤백 절차
        return Map.copyOf(all);
    }
}
