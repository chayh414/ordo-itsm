package com.ordo.itsm.triage.dto;

import com.ordo.itsm.ticket.TicketStatus;
import com.ordo.itsm.triage.TriageResult;
import com.ordo.itsm.triage.TriageResultStatus;

public record TriageResponse(
        int attemptNo,
        TriageResultStatus resultStatus,
        String modelName,
        Double confidence,
        boolean needsReview,          // 신뢰도 0.7 미만 → 운영 담당자 검토 필요
        TriageResult result,          // 실패 시 null
        String message,               // 실패 사유
        TicketStatus ticketStatus,    // 분석 후 티켓 상태
        boolean slaPaused
) {
    public static final double REVIEW_THRESHOLD = 0.7;
}
