package com.ordo.itsm.ticket;

public enum TicketStatus {
    DRAFT,
    SUBMITTED,          // 접수
    TRIAGED,            // AI 분석 확인 완료
    INFO_REQUIRED,      // 고객 보완 대기 (SLA 정지)
    RISK_REVIEWED,      // 위험도 산정 완료
    PENDING_APPROVAL,   // 승인 대기
    APPROVED,
    REJECTED,
    ASSIGNED,
    SCHEDULED,
    IN_PROGRESS,
    RESOLVED,           // 장애·일반 요청 해결
    COMPLETED,          // 변경 작업 완료
    VERIFIED,           // 작업 후 검증 완료
    CLOSED,
    CANCELLED
}
