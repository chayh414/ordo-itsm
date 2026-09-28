package com.ordo.itsm.sla;

import com.ordo.itsm.ticket.Priority;

import java.time.OffsetDateTime;

public record SlaResponse(
        Priority basis,               // 마감 계산 기준 우선순위
        OffsetDateTime responseDueAt,
        OffsetDateTime resolutionDueAt,
        boolean paused,
        int pausedTotalMinutes,
        boolean responseBreached,
        boolean resolutionBreached
) {
    public static SlaResponse from(TicketSla sla) {
        if (sla == null) {
            return null;
        }
        return new SlaResponse(
                sla.getPolicy().getPriority(),
                sla.getResponseDueAt(),
                sla.getResolutionDueAt(),
                sla.isPaused(),
                sla.getPausedTotalMinutes(),
                sla.isResponseBreached(),
                sla.isResolutionBreached()
        );
    }
}
