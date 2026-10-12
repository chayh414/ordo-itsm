package com.ordo.itsm.risk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {

    Optional<RiskAssessment> findByTicket_IdAndCurrentTrue(Long ticketId);
}
