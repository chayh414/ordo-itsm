package com.ordo.itsm.sla;

import com.ordo.itsm.ticket.Priority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {
    Optional<SlaPolicy> findByTenant_IdAndPriority(Long tenantId, Priority priority);
}
