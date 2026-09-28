package com.ordo.itsm.sla;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketSlaRepository extends JpaRepository<TicketSla, Long> {

    Optional<TicketSla> findByTicket_Id(Long ticketId);

    List<TicketSla> findByTicket_IdIn(Collection<Long> ticketIds);
}
