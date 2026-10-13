package com.ordo.itsm.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    /** 백오피스 상세의 감사 로그 섹션용 — 최신순. */
    List<AuditLog> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
}
