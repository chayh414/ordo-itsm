package com.ordo.itsm.ticket.dto;

import com.ordo.itsm.sla.SlaResponse;
import com.ordo.itsm.sla.TicketSla;
import com.ordo.itsm.ticket.*;

import java.time.OffsetDateTime;
import java.util.Map;

public record TicketDetailResponse(
        Long id,
        String ticketNo,
        Ref tenant,
        Ref requester,
        String subject,
        String originalRequest,
        RequestType requestType,
        String category,
        TicketStatus status,
        Priority priority,
        RiskGrade riskGrade,
        EnvironmentType environment,
        String affectedService,
        OffsetDateTime scheduledAt,
        Map<String, Object> details,
        SlaResponse sla,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record Ref(Long id, String name) {
    }

    public static TicketDetailResponse from(Ticket t, TicketSla sla, Map<String, Object> details) {
        return new TicketDetailResponse(
                t.getId(),
                t.getTicketNo(),
                new Ref(t.getTenant().getId(), t.getTenant().getName()),
                new Ref(t.getRequester().getId(), t.getRequester().getName()),
                t.getSubject(),
                t.getOriginalRequest(),
                t.getRequestType(),
                t.getCategory(),
                t.getStatus(),
                t.getPriority(),
                t.getRiskGrade(),
                t.getEnvironment(),
                t.getAffectedService(),
                t.getScheduledAt(),
                details,
                SlaResponse.from(sla),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
