package com.ordo.itsm.assistant.tool;

import com.ordo.itsm.global.exception.BusinessException;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.ticket.TicketService;
import com.ordo.itsm.ticket.dto.TicketDetailResponse;
import com.ordo.itsm.triage.TriageService;
import com.ordo.itsm.triage.dto.TriageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 티켓번호(ORD-YYYYMMDD-NNNN)로 요청 상세 조회. 트리아지 최신 결과가 있으면 함께 반환. */
@Component
@RequiredArgsConstructor
public class GetRequestTool implements AssistantTool {

    public static final String NAME = "get_request";

    private final TicketService ticketService;
    private final TriageService triageService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "티켓번호(ORD-YYYYMMDD-NNNN) 로 요청 상세를 조회한다. 티켓번호는 사용자 원문에 있는 값을 그대로 전달한다.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "ticketNo", Map.of("type", "string",
                                "description", "티켓번호 (예: ORD-20261008-0001)")
                ),
                "required", List.of("ticketNo"),
                "additionalProperties", false
        );
    }

    @Override
    public ToolResult execute(AuthUser user, Map<String, Object> args) {
        String ticketNo = CreateRequestTool.asString(args, "ticketNo");
        if (ticketNo == null || ticketNo.isBlank()) {
            return ToolResult.error("ticketNo 를 전달해 주세요.");
        }

        TicketDetailResponse detail = ticketService.getByTicketNo(user, ticketNo.trim());

        TriageResponse triage = null;
        try {
            triage = triageService.latest(user, detail.id());
        } catch (BusinessException ignored) {
            // 트리아지 결과가 아직 없을 수 있음 — 상세만 돌려준다
        }

        StringBuilder llm = new StringBuilder();
        llm.append(detail.ticketNo()).append(" — 상태=").append(detail.status())
                .append(", 분류=").append(detail.category() == null ? "미확정" : detail.category());
        if (detail.sla() != null) {
            llm.append(", 해결기한=").append(detail.sla().resolutionDueAt());
            if (detail.sla().paused()) llm.append(" (SLA 정지)");
        }
        if (triage != null && triage.result() != null && !triage.result().missingFields().isEmpty()) {
            llm.append(", 누락 정보=").append(String.join(",", triage.result().missingFields()));
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("ticket", detail);
        if (triage != null) card.put("triage", triage);
        return ToolResult.ok(llm.toString(), card);
    }
}
