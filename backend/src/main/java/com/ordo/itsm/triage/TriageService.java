package com.ordo.itsm.triage;

import com.ordo.itsm.audit.AuditAction;
import com.ordo.itsm.audit.AuditLogService;
import com.ordo.itsm.global.exception.BusinessException;
import com.ordo.itsm.global.exception.ErrorCode;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.sla.TicketSla;
import com.ordo.itsm.sla.TicketSlaRepository;
import com.ordo.itsm.tenant.TenantAccessService;
import com.ordo.itsm.ticket.Ticket;
import com.ordo.itsm.ticket.TicketRepository;
import com.ordo.itsm.ticket.TicketStatus;
import com.ordo.itsm.triage.dto.TriageResponse;
import com.ordo.itsm.triage.engine.TriageEngine;
import com.ordo.itsm.triage.engine.TriageEngineException;
import com.ordo.itsm.triage.engine.TriageOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriageService {

    /** 트리아지 결과로 상태를 바꿔도 되는 단계 (작업이 진행된 티켓은 건드리지 않음) */
    private static final Set<TicketStatus> TRIAGEABLE = Set.of(
            TicketStatus.SUBMITTED, TicketStatus.INFO_REQUIRED, TicketStatus.TRIAGED);

    private final TicketRepository ticketRepository;
    private final TicketSlaRepository ticketSlaRepository;
    private final AiTriageRecordRepository recordRepository;
    private final TenantAccessService tenantAccessService;
    private final SensitiveDataMasker masker;
    private final TriageEngine engine;
    private final TriageValidator validator;
    private final AuditLogService auditLogService;
    private final JsonMapper jsonMapper;

    /**
     * 트리아지 실행 (② 마스킹 → ③ AI 분석 → ④ 서버 검증 → ⑥ 누락 시 INFO_REQUIRED + SLA 정지).
     * AI가 실패해도 예외를 던지지 않고 실패 기록을 남긴 뒤 결과를 돌려준다 (요청 자체는 유지).
     */
    @Transactional
    public TriageResponse run(AuthUser user, Long ticketId) {
        Ticket ticket = loadAccessible(user, ticketId);
        int attemptNo = recordRepository.countByTicket_Id(ticketId) + 1;

        String masked = masker.mask(ticket.getSubject() + "\n" + ticket.getOriginalRequest());

        TriageResult result;
        try {
            TriageOutcome outcome = engine.analyze(masked);
            result = validator.validate(outcome.result());
        } catch (TriageEngineException e) {
            return recordFailure(user, ticket, attemptNo, e);
        }

        recordRepository.save(AiTriageRecord.of(ticket, attemptNo, engine.modelName(),
                TriageResultStatus.SUCCESS, jsonMapper.writeValueAsString(result), result.confidence()));

        boolean slaPaused = applyToTicket(user, ticket, result);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("attemptNo", attemptNo);
        after.put("model", engine.modelName());
        after.put("requestType", result.requestType().name());
        after.put("category", result.category());
        after.put("missingFields", result.missingFields());
        after.put("riskFactors", result.riskFactors());
        after.put("confidence", result.confidence());
        after.put("ticketStatus", ticket.getStatus().name());
        auditLogService.record(AuditAction.AI_TRIAGED, user, ticket.getTenant().getId(), ticket.getId(), null, after);

        return new TriageResponse(attemptNo, TriageResultStatus.SUCCESS, engine.modelName(),
                result.confidence(), result.confidence() < TriageResponse.REVIEW_THRESHOLD,
                result, null, ticket.getStatus(), slaPaused || isSlaPaused(ticketId));
    }

    /** 최신 트리아지 결과 */
    @Transactional(readOnly = true)
    public TriageResponse latest(AuthUser user, Long ticketId) {
        Ticket ticket = loadAccessible(user, ticketId);
        AiTriageRecord record = recordRepository.findTopByTicket_IdOrderByAttemptNoDesc(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND, "AI 분석 결과가 없습니다."));

        TriageResult result = record.getResultStatus() == TriageResultStatus.SUCCESS
                ? jsonMapper.readValue(record.getResultJson(), TriageResult.class)
                : null;
        Double confidence = record.getConfidence() == null ? null : record.getConfidence().doubleValue();

        return new TriageResponse(record.getAttemptNo(), record.getResultStatus(), record.getModelName(),
                confidence, confidence == null || confidence < TriageResponse.REVIEW_THRESHOLD,
                result, null, ticket.getStatus(), isSlaPaused(ticketId));
    }

    private boolean applyToTicket(AuthUser user, Ticket ticket, TriageResult result) {
        if (!TRIAGEABLE.contains(ticket.getStatus())) {
            return false;
        }
        ticket.applyTriage(result.requestType(), result.category(), result.environment(), result.affectedService());

        if (result.missingFields().isEmpty()) {
            return false;
        }

        TicketStatus before = ticket.getStatus();
        ticket.changeStatus(TicketStatus.INFO_REQUIRED);
        if (before != TicketStatus.INFO_REQUIRED) {
            auditLogService.record(AuditAction.STATUS_CHANGED, user, ticket.getTenant().getId(), ticket.getId(),
                    Map.of("status", before.name()),
                    Map.of("status", TicketStatus.INFO_REQUIRED.name(), "reason", "MISSING_FIELDS"));
        }

        TicketSla sla = ticketSlaRepository.findByTicket_Id(ticket.getId()).orElse(null);
        if (sla != null && sla.pause(OffsetDateTime.now())) {
            auditLogService.record(AuditAction.SLA_PAUSED, user, ticket.getTenant().getId(), ticket.getId(),
                    null, Map.of("reason", "INFO_REQUIRED"));
            return true;
        }
        return false;
    }

    private TriageResponse recordFailure(AuthUser user, Ticket ticket, int attemptNo, TriageEngineException e) {
        TriageResultStatus status = e.getKind() == TriageEngineException.Kind.INVALID_JSON
                ? TriageResultStatus.INVALID_JSON
                : TriageResultStatus.FAILED;

        String raw = e.getRawResponse();
        String resultJson = raw == null ? null
                : jsonMapper.writeValueAsString(Map.of("error", e.getMessage(), "raw", truncate(raw, 2000)));
        recordRepository.save(AiTriageRecord.of(ticket, attemptNo, engine.modelName(), status, resultJson, null));

        auditLogService.record(AuditAction.AI_TRIAGE_FAILED, user, ticket.getTenant().getId(), ticket.getId(),
                null, Map.of("attemptNo", attemptNo, "status", status.name(), "message", e.getMessage()));

        return new TriageResponse(attemptNo, status, engine.modelName(), null, true, null,
                e.getMessage() + " — 재시도하거나 수동으로 분류해 주세요.",
                ticket.getStatus(), isSlaPaused(ticket.getId()));
    }

    private Ticket loadAccessible(AuthUser user, Long ticketId) {
        Ticket ticket = ticketRepository.findWithTenantAndRequesterById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
        if (!tenantAccessService.scopeOf(user).includes(ticket.getTenant().getId())) {
            auditLogService.recordIndependently(AuditAction.ACCESS_DENIED, user, user.tenantId(), ticketId, null,
                    Map.of("reason", "TENANT_MISMATCH", "target", "TRIAGE"));
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "다른 고객사의 티켓에는 접근할 수 없습니다.");
        }
        return ticket;
    }

    private boolean isSlaPaused(Long ticketId) {
        return ticketSlaRepository.findByTicket_Id(ticketId).map(TicketSla::isPaused).orElse(false);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
