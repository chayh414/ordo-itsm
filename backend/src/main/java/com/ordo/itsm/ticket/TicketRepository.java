package com.ordo.itsm.ticket;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @EntityGraph(attributePaths = {"tenant", "requester"})
    Optional<Ticket> findWithTenantAndRequesterById(Long id);

    @EntityGraph(attributePaths = {"tenant", "requester"})
    Optional<Ticket> findByTicketNo(String ticketNo);

    /* ----- 운영 관리자: 전체 고객사. 다른 사람 DRAFT 는 숨기고 자신의 DRAFT 는 유지 ----- */

    @EntityGraph(attributePaths = {"tenant", "requester"})
    @Query("""
            SELECT t FROM Ticket t
            WHERE t.status <> :excludeStatus OR t.requester.id = :userId
            """)
    Page<Ticket> findAllVisible(@Param("excludeStatus") TicketStatus excludeStatus,
                                @Param("userId") Long userId,
                                Pageable pageable);

    @EntityGraph(attributePaths = {"tenant", "requester"})
    @Query("""
            SELECT t FROM Ticket t
            WHERE t.status = :status
              AND (t.status <> :excludeStatus OR t.requester.id = :userId)
            """)
    Page<Ticket> findByStatusVisible(@Param("status") TicketStatus status,
                                     @Param("excludeStatus") TicketStatus excludeStatus,
                                     @Param("userId") Long userId,
                                     Pageable pageable);

    /* ----- 그 외: 허용된 고객사만 ----- */

    @EntityGraph(attributePaths = {"tenant", "requester"})
    @Query("""
            SELECT t FROM Ticket t
            WHERE t.tenant.id IN :tenantIds
              AND (t.status <> :excludeStatus OR t.requester.id = :userId)
            """)
    Page<Ticket> findByTenantInVisible(@Param("tenantIds") Collection<Long> tenantIds,
                                       @Param("excludeStatus") TicketStatus excludeStatus,
                                       @Param("userId") Long userId,
                                       Pageable pageable);

    @EntityGraph(attributePaths = {"tenant", "requester"})
    @Query("""
            SELECT t FROM Ticket t
            WHERE t.tenant.id IN :tenantIds
              AND t.status = :status
              AND (t.status <> :excludeStatus OR t.requester.id = :userId)
            """)
    Page<Ticket> findByTenantInAndStatusVisible(@Param("tenantIds") Collection<Long> tenantIds,
                                                @Param("status") TicketStatus status,
                                                @Param("excludeStatus") TicketStatus excludeStatus,
                                                @Param("userId") Long userId,
                                                Pageable pageable);

    /* ----- 어시스턴트 전용: 본인(REQUESTER) 요청만 ----- */

    @EntityGraph(attributePaths = {"tenant", "requester"})
    Page<Ticket> findByRequester_Id(Long requesterId, Pageable pageable);

    @EntityGraph(attributePaths = {"tenant", "requester"})
    Page<Ticket> findByRequester_IdAndStatus(Long requesterId, TicketStatus status, Pageable pageable);

    long countByTicketNoStartingWith(String prefix);
}
