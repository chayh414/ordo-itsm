package com.ordo.itsm.ticket.dto;

import com.ordo.itsm.sla.SlaResponse;
import com.ordo.itsm.ticket.TicketStatus;
import com.ordo.itsm.triage.dto.TriageResponse;

import java.time.OffsetDateTime;

public record TicketCreatedResponse(
        Long id,
        String ticketNo,
        TicketStatus status,
        SlaResponse sla,
        TriageResponse triage,
        OffsetDateTime createdAt
) {
    public TicketCreatedResponse withTriage(TriageResponse triage) {
        TicketStatus latestStatus = triage != null ? triage.ticketStatus() : status;
        return new TicketCreatedResponse(id, ticketNo, latestStatus, sla, triage, createdAt);
    }
}
