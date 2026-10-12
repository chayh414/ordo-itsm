package com.ordo.itsm.triage.dto;

import com.ordo.itsm.ticket.RiskGrade;
import com.ordo.itsm.ticket.TicketStatus;
import com.ordo.itsm.triage.TriageResult;
import com.ordo.itsm.triage.TriageResultStatus;

import java.util.Map;

/**
 * 트리아지 결과 응답.
 * - result: 분류·누락·위험 요인 등 (실패 시 null)
 * - riskScore/riskGrade/factorPoints: RiskPolicyService 가 산정한 위험도. CHANGE 아니면 전부 null.
 */
public record TriageResponse(
        int attemptNo,
        TriageResultStatus resultStatus,
        String modelName,
        Double confidence,
        boolean needsReview,
        TriageResult result,
        String message,
        TicketStatus ticketStatus,
        boolean slaPaused,
        Integer riskScore,
        RiskGrade riskGrade,
        Map<String, Integer> factorPoints
) {
    public static final double REVIEW_THRESHOLD = 0.7;
}
