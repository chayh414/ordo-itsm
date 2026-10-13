package com.ordo.itsm.risk;

import com.ordo.itsm.audit.AuditAction;
import com.ordo.itsm.audit.AuditLogService;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.ticket.RequestType;
import com.ordo.itsm.ticket.Ticket;
import com.ordo.itsm.triage.TriageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TriageService 가 트리아지 결과를 반영한 뒤 호출한다. 저장·이력 토글·감사·ticket.riskGrade 갱신까지 담당.
 *
 * 규칙은 {@link RiskPolicy} 가 순수 로직으로 가지고 있어 테스트는 그쪽에서 커버한다.
 * 이 서비스는 "변경 요청(CHANGE)에만 평가" 정책과 영속화 처리를 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskPolicyService {

    private final RiskAssessmentRepository repository;
    private final AuditLogService auditLogService;
    private final JsonMapper jsonMapper;

    /**
     * 트리아지 결과를 바탕으로 위험도를 평가한다.
     * - requestType != CHANGE 이면 null 반환 (장애·문의 등은 위험 점수 없음)
     * - 평가 성공 시: 이전 current 행 is_current=false, 새 행 저장, ticket.riskGrade 갱신, 감사 로그
     * - 재평가(이전 행이 있으면)면 RISK_REASSESSED, 처음이면 RISK_ASSESSED
     */
    @Transactional
    public RiskPolicy.Outcome assess(AuthUser user, Ticket ticket, TriageResult result) {
        if (result == null || result.requestType() != RequestType.CHANGE) {
            return null;
        }

        RiskPolicy.Outcome outcome = RiskPolicy.score(result.riskFactors());

        RiskAssessment previous = repository.findByTicket_IdAndCurrentTrue(ticket.getId()).orElse(null);
        if (previous != null) {
            previous.markSuperseded();
            // Hibernate 기본 flush 순서는 INSERT → UPDATE 라서, 새 current=true 가 먼저 들어가며
            // unique index uq_risk_current (ticket_id) WHERE is_current 를 위반한다.
            // 명시 flush 로 UPDATE 를 먼저 반영 후 새 INSERT.
            repository.flush();
        }

        String factorsJson = jsonMapper.writeValueAsString(outcome.factorPoints());
        repository.save(RiskAssessment.create(ticket, outcome.score(), outcome.grade(),
                factorsJson, RiskPolicy.VERSION));

        ticket.applyRisk(outcome.grade());

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("score", outcome.score());
        after.put("grade", outcome.grade().name());
        after.put("factorPoints", outcome.factorPoints());
        after.put("policyVersion", RiskPolicy.VERSION);

        if (previous == null) {
            auditLogService.record(AuditAction.RISK_ASSESSED, user, ticket.getTenant().getId(),
                    ticket.getId(), null, after);
        } else {
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("score", previous.getScore());
            before.put("grade", previous.getGrade().name());
            auditLogService.record(AuditAction.RISK_REASSESSED, user, ticket.getTenant().getId(),
                    ticket.getId(), before, after);
        }
        return outcome;
    }
}
