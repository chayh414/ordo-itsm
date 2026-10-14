package com.ordo.itsm.assistant.tool;

import com.ordo.itsm.global.common.PageResponse;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.ticket.TicketService;
import com.ordo.itsm.ticket.TicketStatus;
import com.ordo.itsm.ticket.dto.TicketSummaryResponse;
import com.ordo.itsm.triage.TriageLabels;
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
        // 사용자가 상태(보완 대기·접수·완료 등)를 말하면 status 인자를 반드시 넣게 유도.
        return "내 요청 목록을 최신순 10건 조회한다. 사용자가 상태(예: 보완 대기, 접수, 완료)를 말하면"
                + " status 인자를 반드시 넣을 것 (enum 값으로). 미지정 시 전체 상태.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "status", Map.of(
                                "type", "string",
                                "enum", Arrays.stream(TicketStatus.values()).map(Enum::name).toList(),
                                "description", "티켓 상태 필터 (선택). INFO_REQUIRED=보완 대기, SUBMITTED=접수됨 등."
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

        return ToolResult.ok(ListFormat.render(page, null, status), page);
    }

    /**
     * 상태 값 파싱. enum 이름 (INFO_REQUIRED) 과 한글 라벨 ("보완 대기") 둘 다 수용.
     * AI 가 사용자 질문의 한글 표현을 그대로 넘기는 경우가 많아 역매핑을 추가함.
     */
    static TicketStatus parseStatus(Map<String, Object> args) {
        Object v = args == null ? null : args.get("status");
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return TicketStatus.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ignored) { /* 한글 라벨 시도 */ }
        for (TicketStatus st : TicketStatus.values()) {
            if (TriageLabels.status(st.name()).equals(s)) return st;
        }
        return null;
    }
}
