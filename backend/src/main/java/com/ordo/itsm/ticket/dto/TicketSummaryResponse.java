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
        String subject,
        RequestType requestType,
        TicketStatus status,
        Priority priority,
        RiskGrade riskGrade,
        SlaResponse sla,
        OffsetDateTime createdAt
) {
    public static TicketSummaryResponse from(Ticket t, TicketSla sla) {
        return new TicketSummaryResponse(
                t.getId(),
                t.getTicketNo(),
                t.getTenant().getName(),
                t.getRequester().getName(),
                t.getSubject(),
                t.getRequestType(),
                t.getStatus(),
                t.getPriority(),
                t.getRiskGrade(),
                SlaResponse.from(sla),
                t.getCreatedAt()
        );
    }
}
