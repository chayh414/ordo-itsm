package com.ordo.itsm.ticket;

import com.ordo.itsm.global.exception.BusinessException;
import com.ordo.itsm.global.exception.ErrorCode;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.sla.TicketSla;
import com.ordo.itsm.sla.TicketSlaRepository;
import com.ordo.itsm.ticket.dto.CreateTicketRequest;
import com.ordo.itsm.ticket.dto.TicketCreatedResponse;
import com.ordo.itsm.ticket.dto.TicketSummaryResponse;
import com.ordo.itsm.triage.TriageService;
import com.ordo.itsm.user.User;
import com.ordo.itsm.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DRAFT 생성·접수 플로우 통합 테스트.
 * 로컬 PostgreSQL(5433) 가 필요하며, @Transactional 로 각 테스트 종료 시 롤백한다.
 */
@SpringBootTest
@Transactional
class TicketDraftSubmitIT {

    @Autowired TicketService ticketService;
    @Autowired TriageService triageService;
    @Autowired TicketRepository ticketRepository;
    @Autowired TicketSlaRepository ticketSlaRepository;
    @Autowired UserRepository userRepository;

    private AuthUser requesterA;    // 요청자 본인
    private AuthUser requesterB;    // 다른 테넌트 — 목록 가시성 테스트에서 사용

    @BeforeEach
    void setUp() {
        User a = userRepository.findByEmail("requester@a-univ.demo").orElseThrow();
        User b = userRepository.findByEmail("requester@b-bank.demo").orElseThrow();
        requesterA = new AuthUser(a.getId(), a.getTenant().getId(), a.getRole(), a.getEmail());
        requesterB = new AuthUser(b.getId(), b.getTenant().getId(), b.getRole(), b.getEmail());
    }

    @Test
    void createDraft_isDraft_withNoSla() {
        TicketCreatedResponse res = ticketService.createDraft(requesterA,
                new CreateTicketRequest("초안 제목", "방화벽 포트 열어주세요. 3306."));

        assertEquals(TicketStatus.DRAFT, res.status(), "status=DRAFT 로 생성");
        assertNull(res.sla(), "SLA 는 submit 시점에 시작");
        assertTrue(ticketSlaRepository.findByTicket_Id(res.id()).isEmpty(), "SLA 행 없음");
    }

    @Test
    void submit_transitionsAndStartsSla() {
        TicketCreatedResponse draft = ticketService.createDraft(requesterA,
                new CreateTicketRequest("접수 전 제목", "운영 DB 컬럼 추가 요청."));

        ticketService.submit(requesterA, draft.id());

        Ticket after = ticketRepository.findWithTenantAndRequesterById(draft.id()).orElseThrow();
        assertEquals(TicketStatus.SUBMITTED, after.getStatus(), "DRAFT → SUBMITTED");
        TicketSla sla = ticketSlaRepository.findByTicket_Id(draft.id()).orElse(null);
        assertNotNull(sla, "submit 시점에 SLA 시작");
        assertNotNull(sla.getResolutionDueAt());
    }

    // 비 소유자 submit 거부 경로는 recordIndependently(REQUIRES_NEW) 로 감사로그를 쓰기 때문에,
    // 테스트 @Transactional 안에서 생성한 DRAFT 가 아직 커밋되지 않아 FK 가 걸린다 (운영에선 선행 커밋되므로 안전).
    // 해당 경로는 추후 MVC 통합 테스트/시연 스크립트에서 커버한다.

    @Test
    void submit_whenNotDraft_isConflict() {
        TicketCreatedResponse draft = ticketService.createDraft(requesterA,
                new CreateTicketRequest("초안", "원문"));
        ticketService.submit(requesterA, draft.id());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ticketService.submit(requesterA, draft.id()));
        assertEquals(ErrorCode.TICKET_INVALID_TRANSITION, ex.getErrorCode());
    }

    @Test
    void list_hidesDraftsFromOtherTenant_showsOwn() {
        // A 가 DRAFT 생성 (SLA 없음 → 목록에 보일 때 sla=null 허용)
        TicketCreatedResponse draft = ticketService.createDraft(requesterA,
                new CreateTicketRequest("A 초안", "원문"));

        // B 는 다른 테넌트라 원래 보이지 않음 (가시성 OR DRAFT 규칙 이전에 scope 이 걸러줌)
        var listedForB = ticketService.list(requesterB, null, PageRequest.of(0, 50)).content();
        assertTrue(listedForB.stream().noneMatch(t -> t.id().equals(draft.id())),
                "다른 테넌트 사용자는 A 의 DRAFT 를 볼 수 없음");

        // A 본인에게는 자기 DRAFT 가 보여야 한다
        var listedForA = ticketService.list(requesterA, null, PageRequest.of(0, 50)).content();
        assertTrue(listedForA.stream().anyMatch(t -> t.id().equals(draft.id())),
                "본인은 자기 DRAFT 를 볼 수 있어야 함");
    }

    @Test
    void applyLatestToTicket_isNoop_whenNoTriageRecord() {
        TicketCreatedResponse draft = ticketService.createDraft(requesterA,
                new CreateTicketRequest("초안", "원문"));
        ticketService.submit(requesterA, draft.id());

        // 트리아지 레코드가 없는 상태에서 호출해도 예외 없이 조용히 종료
        assertDoesNotThrow(() -> triageService.applyLatestToTicket(requesterA, draft.id()));
    }
}
