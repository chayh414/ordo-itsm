package com.ordo.itsm.assistant.tool;

import com.ordo.itsm.global.common.PageResponse;
import com.ordo.itsm.ticket.TicketStatus;
import com.ordo.itsm.ticket.dto.TicketSummaryResponse;
import com.ordo.itsm.triage.TriageLabels;

import java.util.List;

/**
 * 목록 도구(list_my_requests, list_requests)의 llmText 포맷을 통일.
 *
 * 첫 줄에 "서버가 실제 적용한 조건" 을 명시한다 — AI 가 자연어 답변을 만들 때 사용자 질문의 표현을
 * 그대로 복사하지 않도록(예: "보완 대기" 라고 묻고 실제로는 전체 반환됐을 때).
 */
final class ListFormat {

    private ListFormat() {}

    static String render(PageResponse<TicketSummaryResponse> page,
                         String tenantLabel,
                         TicketStatus statusFilter) {
        String tenantPart = (tenantLabel == null || tenantLabel.isBlank()) ? "전체 고객사" : tenantLabel;
        String statusPart = statusFilter == null ? "전체 상태" : TriageLabels.status(statusFilter.name());
        int count = page.content().size();

        long total = page.totalElements();
        StringBuilder sb = new StringBuilder();
        sb.append("조건: ").append(tenantPart).append(" · ").append(statusPart).append(" · ");
        if (total == 0) {
            sb.append("0건\n조건에 해당하는 요청이 없습니다.");
            return sb.toString();
        }
        if (total == count) {
            sb.append("전체 ").append(total).append("건");
        } else {
            sb.append("전체 ").append(total).append("건 중 최근 ").append(count).append("건");
        }

        List<String> items = page.content().stream()
                .map(ListFormat::item)
                .toList();
        sb.append('\n').append(String.join("\n", items));
        return sb.toString();
    }

    private static String item(TicketSummaryResponse t) {
        String title = t.title() != null && !t.title().isBlank() ? t.title() : t.subject();
        String statusKo = t.status() == null ? "" : TriageLabels.status(t.status().name());
        return "[" + t.ticketNo() + " · " + t.tenantName() + " · " + statusKo + "] " + title;
    }
}
