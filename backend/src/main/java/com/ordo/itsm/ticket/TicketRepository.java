package com.ordo.itsm.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @EntityGraph(attributePaths = {"tenant", "requester"})
    Optional<Ticket> findWithTenantAndRequesterById(Long id);

    /* ----- 운영 관리자: 전체 고객사 ----- */

    @Override
    @EntityGraph(attributePaths = {"tenant", "requester"})
    Page<Ticket> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"tenant", "requester"})
    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);

    /* ----- 그 외: 허용된 고객사만 ----- */

    @EntityGraph(attributePaths = {"tenant", "requester"})
    Page<Ticket> findByTenant_IdIn(Collection<Long> tenantIds, Pageable pageable);

    @EntityGraph(attributePaths = {"tenant", "requester"})
    Page<Ticket> findByTenant_IdInAndStatus(Collection<Long> tenantIds, TicketStatus status, Pageable pageable);

    long countByTicketNoStartingWith(String prefix);
}
