package com.ordo.itsm.ticket;

import com.ordo.itsm.global.common.PageResponse;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.ticket.dto.CreateTicketRequest;
import com.ordo.itsm.ticket.dto.TicketCreatedResponse;
import com.ordo.itsm.ticket.dto.TicketDetailResponse;
import com.ordo.itsm.ticket.dto.TicketSummaryResponse;
import com.ordo.itsm.triage.TriageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TriageService triageService;

    /**
     * 요청 등록 → 커밋 후 AI 트리아지 자동 실행.
     * 트랜잭션을 분리해서 AI가 실패해도 요청 자체는 저장된 상태로 남는다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketCreatedResponse create(@AuthenticationPrincipal AuthUser user,
                                        @Valid @RequestBody CreateTicketRequest request) {
        TicketCreatedResponse created = ticketService.create(user, request);
        return created.withTriage(triageService.run(user, created.id()));
    }

    @GetMapping
    public PageResponse<TicketSummaryResponse> list(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(required = false) TicketStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ticketService.list(user, status, pageable);
    }

    @GetMapping("/{ticketId}")
    public TicketDetailResponse get(@AuthenticationPrincipal AuthUser user,
                                    @PathVariable Long ticketId) {
        return ticketService.get(user, ticketId);
    }
}
