package com.ordo.itsm.triage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiTriageRecordRepository extends JpaRepository<AiTriageRecord, Long> {

    int countByTicket_Id(Long ticketId);

    Optional<AiTriageRecord> findTopByTicket_IdOrderByAttemptNoDesc(Long ticketId);

    List<AiTriageRecord> findByTicket_IdOrderByAttemptNoAsc(Long ticketId);
}
