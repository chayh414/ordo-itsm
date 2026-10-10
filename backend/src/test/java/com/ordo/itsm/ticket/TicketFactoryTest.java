package com.ordo.itsm.ticket;

import com.ordo.itsm.tenant.Tenant;
import com.ordo.itsm.user.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Ticket 엔티티의 팩토리 메서드 단위 테스트 (DB·Spring 없이) */
class TicketFactoryTest {

    @Test
    void create_startsAsSubmitted() {
        Ticket t = Ticket.create("ORD-20261008-0001", null, null, "제목", "내용");
        assertEquals(TicketStatus.SUBMITTED, t.getStatus());
        assertEquals("{}", t.getDetailJson());
    }

    @Test
    void createDraft_startsAsDraft() {
        Ticket t = Ticket.createDraft("ORD-20261008-0002", null, null, "초안", "원문");
        assertEquals(TicketStatus.DRAFT, t.getStatus());
    }

    @Test
    void changeStatus_updatesStatus() {
        Ticket t = Ticket.createDraft("ORD-20261008-0003", null, null, "x", "y");
        t.changeStatus(TicketStatus.SUBMITTED);
        assertEquals(TicketStatus.SUBMITTED, t.getStatus());
    }
}
