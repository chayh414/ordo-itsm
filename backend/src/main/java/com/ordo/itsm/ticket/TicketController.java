package com.ordo.itsm.ticket;

import com.ordo.itsm.global.common.PageResponse;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.ticket.dto.CreateTicketRequest;
import com.ordo.itsm.ticket.dto.SupplementRequest;
import com.ordo.itsm.ticket.dto.SupplementResponse;
import com.ordo.itsm.ticket.dto.TicketCreatedResponse;
import com.ordo.itsm.ticket.dto.TicketDetailResponse;
import com.ordo.itsm.ticket.dto.TicketSummaryResponse;
import com.ordo.itsm.triage.TriageService;
import com.ordo.itsm.triage.dto.TriageResponse;
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

    /**
     * DRAFT 티켓(어시스턴트가 만든 초안)을 사용자가 명시적으로 접수한다.
     * 접수 직후 트리아지 결과를 재적용해 누락 정보가 있으면 INFO_REQUIRED + SLA 정지가 반영된다.
     */
    @PostMapping("/{ticketId}/submit")
    public TicketDetailResponse submit(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable Long ticketId) {
        ticketService.submit(user, ticketId);
        triageService.applyLatestToTicket(user, ticketId);
        return ticketService.get(user, ticketId);
    }

    /**
     * "확인이 필요한 내용" 보완.
     * detail_json 에 답변 저장 → 트리아지 재실행 (supplements 반영) → 위험도 재평가 →
     * 상태·SLA 재적용. 응답은 갱신된 ticket + triage 를 한 번에.
     */
    @PostMapping("/{ticketId}/supplement")
    public SupplementResponse supplement(@AuthenticationPrincipal AuthUser user,
                                         @PathVariable Long ticketId,
                                         @Valid @RequestBody SupplementRequest request) {
        ticketService.supplement(user, ticketId, request.field(), request.answer());
        TriageResponse triage = triageService.rerunWithSupplements(user, ticketId,
                ticketService.getSupplements(ticketId));
        TicketDetailResponse detail = ticketService.get(user, ticketId);
        return new SupplementResponse(detail, triage);
    }
}
