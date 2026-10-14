package com.ordo.itsm.ticket.dto;

import com.ordo.itsm.sla.SlaResponse;
import com.ordo.itsm.sla.TicketSla;
import com.ordo.itsm.ticket.*;

import java.time.OffsetDateTime;

public record TicketSummaryResponse(
        Long id,
        String ticketNo,
        String tenantName,
        String requesterName,
        /** AI 가 요약한 "무엇을 하는지" 20자. 없으면 null (프론트가 subject fallback). */
        String title,
        String subject,
        RequestType requestType,
        TicketStatus status,
        Priority priority,
        RiskGrade riskGrade,
        SlaResponse sla,
        OffsetDateTime createdAt
) {
    public static TicketSummaryResponse from(Ticket t, TicketSla sla, String title) {
        return new TicketSummaryResponse(
                t.getId(),
                t.getTicketNo(),
                t.getTenant().getName(),
                t.getRequester().getName(),
                title,
                t.getSubject(),
                t.getRequestType(),
                t.getStatus(),
                t.getPriority(),
                t.getRiskGrade(),
                SlaResponse.from(sla),
                t.getCreatedAt()
        );
    }

    /** title 모르면 null 로 (기존 호출부 호환용). */
    public static TicketSummaryResponse from(Ticket t, TicketSla sla) {
        return from(t, sla, null);
    }
}
