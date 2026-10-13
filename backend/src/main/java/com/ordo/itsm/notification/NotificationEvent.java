package com.ordo.itsm.notification;

import java.time.OffsetDateTime;

/**
 * SSE 로 프론트에 보내는 실시간 알림.
 * type 으로 종류 구분 (1단계: INCIDENT_CREATED, 2단계: SLA_NEAR).
 *
 * - ticketId / ticketNo: 상세 이동용
 * - tenantId / tenantName: 범위 필터 + 화면 표시
 * - title: 토스트 본문 핵심 (장애 영향 서비스, 요청 제목 등)
 * - summary: 보조 설명 (오류 메시지, 분류 등)
 * - dueAt: 토스트가 "남은 시간" 계산할 때 쓰는 기준 (서버 OffsetDateTime, 프론트에서 1초마다 차감)
 * - issuedAt: 서버 발행 시각 (프론트 디버깅용)
 */
public record NotificationEvent(
        Type type,
        Long ticketId,
        String ticketNo,
        Long tenantId,
        String tenantName,
        /** 티켓 작성자 id. 요청자(REQUESTER)의 "자기 요청만" 필터링에 사용. INCIDENT 는 null 가능. */
        Long requesterId,
        String title,
        String summary,
        OffsetDateTime dueAt,
        OffsetDateTime issuedAt
) {
    public enum Type {
        INCIDENT_CREATED,
        SLA_NEAR
    }
}
