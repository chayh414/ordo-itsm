package com.ordo.itsm.triage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiTriageRecordRepository extends JpaRepository<AiTriageRecord, Long> {

    int countByTicket_Id(Long ticketId);

    Optional<AiTriageRecord> findTopByTicket_IdOrderByAttemptNoDesc(Long ticketId);

    List<AiTriageRecord> findByTicket_IdOrderByAttemptNoAsc(Long ticketId);

    /**
     * 여러 티켓의 SUCCESS 레코드 (각 티켓의 모든 성공 시도).
     * 호출자가 ticket_id 당 attemptNo 최댓값을 골라 "최신 성공" 1건을 뽑는다.
     * 목록 화면이 AI 요약 title 을 보여줄 때 batch 로 쓴다.
     */
    @Query("""
           select r from AiTriageRecord r
           where r.ticket.id in :ticketIds
             and r.resultStatus = com.ordo.itsm.triage.TriageResultStatus.SUCCESS
           order by r.ticket.id asc, r.attemptNo desc
           """)
    List<AiTriageRecord> findSuccessByTicketIds(@Param("ticketIds") Collection<Long> ticketIds);
}
