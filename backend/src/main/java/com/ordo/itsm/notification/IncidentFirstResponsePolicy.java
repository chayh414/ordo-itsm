package com.ordo.itsm.notification;

import com.ordo.itsm.sla.TicketSla;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 장애 "1차 통보 기한" 계산.
 * SLA 응답 기한(responseDueAt) 과 동일한 지표로 통합 — 토스트·장애 상세·백오피스 SLA 가 전부 같은 값을 쓴다.
 * (별도 설정값은 두지 않는다. SLA 정책이 유일한 출처.)
 */
@Component
public class IncidentFirstResponsePolicy {

    /** SLA 가 시작된 티켓의 1차 통보 기한. SLA 응답 기한과 같다. */
    public OffsetDateTime firstResponseDueAt(TicketSla sla) {
        return sla == null ? null : sla.getResponseDueAt();
    }

    /** 지금 기준 남은 시간. 음수면 이미 지남. */
    public Duration remaining(TicketSla sla, OffsetDateTime now) {
        OffsetDateTime due = firstResponseDueAt(sla);
        return due == null ? Duration.ZERO : Duration.between(now, due);
    }
}
