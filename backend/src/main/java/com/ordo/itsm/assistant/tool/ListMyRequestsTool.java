package com.ordo.itsm.assistant.tool;

import com.ordo.itsm.global.common.PageResponse;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.ticket.TicketService;
import com.ordo.itsm.ticket.TicketStatus;
import com.ordo.itsm.ticket.dto.TicketSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 내 요청 목록 — REQUESTER 는 본인 작성, CUSTOMER_ADMIN 은 자기 테넌트 전체 (가시성 필터 포함). */
@Component
@RequiredArgsConstructor
public class ListMyRequestsTool implements AssistantTool {

    public static final String NAME = "list_my_requests";
    private static final int PAGE_SIZE = 10;

    private final TicketService ticketService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "내 요청 목록을 최신순 10건 조회한다. status 를 생략하면 전체 상태.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "status", Map.of(
                                "type", "string",
                                "enum", Arrays.stream(TicketStatus.values()).map(Enum::name).toList(),
                                "description", "티켓 상태 필터 (선택)"
                        )
                ),
                "additionalProperties", false
        );
    }

    @Override
    public ToolResult execute(AuthUser user, Map<String, Object> args) {
        TicketStatus status = parseStatus(args);
        PageResponse<TicketSummaryResponse> page = ticketService.listMine(
                user, status, PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt")));

        if (page.content().isEmpty()) {
            return ToolResult.ok("조건에 해당하는 내 요청이 없습니다.", page);
        }

        StringBuilder llm = new StringBuilder();
        llm.append("최신 ").append(page.content().size()).append("건 — ");
        List<String> items = page.content().stream()
                .map(t -> "[" + t.ticketNo() + " " + t.status() + "] " + t.subject())
                .toList();
        llm.append(String.join(" / ", items));
        return ToolResult.ok(llm.toString(), page);
    }

    static TicketStatus parseStatus(Map<String, Object> args) {
        Object v = args == null ? null : args.get("status");
        if (v == null) return null;
        try {
            return TicketStatus.valueOf(v.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;    // 모델이 enum 밖 값을 주면 필터 미적용 (안전쪽)
        }
    }
}
