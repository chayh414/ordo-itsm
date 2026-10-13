package com.ordo.itsm.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 장애 1차 통보 기한 계산.
 * 토스트와 (추후) 백오피스 상세에서 같은 함수를 쓰기 위한 공용 유틸.
 * 실제 값은 시스원 SLA 정책에 맞춰 설정 (yml: ordo.sla.incident-first-response-minutes).
 */
@Component
public class IncidentFirstResponsePolicy {

    private final int firstResponseMinutes;

    public IncidentFirstResponsePolicy(
            @Value("${ordo.sla.incident-first-response-minutes:15}") int firstResponseMinutes) {
        this.firstResponseMinutes = firstResponseMinutes;
    }

    public int firstResponseMinutes() {
        return firstResponseMinutes;
    }

    /** 장애 접수 시각 + 설정값(분) */
    public OffsetDateTime firstResponseDueAt(OffsetDateTime createdAt) {
        return createdAt.plusMinutes(firstResponseMinutes);
    }

    /** 지금 기준 남은 시간. 음수면 이미 지남. */
    public Duration remaining(OffsetDateTime createdAt, OffsetDateTime now) {
        return Duration.between(now, firstResponseDueAt(createdAt));
    }
}
