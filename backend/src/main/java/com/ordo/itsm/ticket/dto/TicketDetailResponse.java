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
        /** 장애(INCIDENT) 요청의 1차 통보 기한. IncidentFirstResponsePolicy 로 계산. 그 외 요청은 null. */
        OffsetDateTime firstResponseDueAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record Ref(Long id, String name) {
    }

    public static TicketDetailResponse from(Ticket t, TicketSla sla, Map<String, Object> details,
                                            OffsetDateTime firstResponseDueAt) {
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
                firstResponseDueAt,
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
