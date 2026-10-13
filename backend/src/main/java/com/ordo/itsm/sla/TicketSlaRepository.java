package com.ordo.itsm.sla;

import com.ordo.itsm.ticket.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketSlaRepository extends JpaRepository<TicketSla, Long> {

    Optional<TicketSla> findByTicket_Id(Long ticketId);

    List<TicketSla> findByTicket_IdIn(Collection<Long> ticketIds);

    /**
     * SLA 임박 알림 후보.
     * - 정지(INFO_REQUIRED) · 해결됨 · 종결 상태 제외
     * - 해결 기한이 threshold 이내
     * ticket·tenant·requester 를 함께 fetch 해 알림 생성 시 추가 쿼리를 피한다.
     */
    @Query("""
           select s from TicketSla s
             join fetch s.ticket t
             join fetch t.tenant
             join fetch t.requester
           where s.pausedAt is null
             and s.resolvedAt is null
             and s.resolutionDueAt <= :threshold
             and t.status not in :terminalStates
           """)
    List<TicketSla> findNearDue(@Param("threshold") OffsetDateTime threshold,
                                @Param("terminalStates") Collection<TicketStatus> terminalStates);
}
