package com.ordo.itsm.sla;

import com.ordo.itsm.ticket.Priority;
import com.ordo.itsm.ticket.Ticket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlaService {

    /** 우선순위 확정 전에는 P3 기준으로 임시 마감을 계산한다 (기획안 7.6) */
    public static final Priority PROVISIONAL_PRIORITY = Priority.P3;

    private final SlaPolicyRepository slaPolicyRepository;
    private final TicketSlaRepository ticketSlaRepository;

    /** 요청 제출(SUBMITTED) 시점에 SLA 타이머 시작 */
    @Transactional
    public TicketSla start(Ticket ticket) {
        Long tenantId = ticket.getTenant().getId();
        SlaPolicy policy = slaPolicyRepository.findByTenant_IdAndPriority(tenantId, PROVISIONAL_PRIORITY)
                .orElseThrow(() -> new IllegalStateException("SLA 정책 없음: tenantId=" + tenantId));
        return ticketSlaRepository.save(TicketSla.start(ticket, policy, ticket.getCreatedAt()));
    }

    /**
     * 보완 완료로 INFO_REQUIRED → SUBMITTED 전환될 때 호출.
     * 정지돼 있던 SLA 를 재개하고, 정지 시간만큼 마감을 뒤로 민다.
     * 반환값은 정지돼 있던 분 수 (0 이면 이미 돌고 있던 상태라 변화 없음).
     */
    @Transactional
    public int resume(TicketSla sla, java.time.OffsetDateTime now) {
        return sla.resume(now);
    }
}
