package com.ordo.itsm.risk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {

    Optional<RiskAssessment> findByTicket_IdAndCurrentTrue(Long ticketId);

    /** 위험 평가 이력 (최신순) — 백오피스 상세에서 11→9→7 궤적 표시용. */
    List<RiskAssessment> findByTicket_IdOrderByCreatedAtDesc(Long ticketId);
}
