package com.ordo.itsm.triage;

import com.ordo.itsm.audit.AuditAction;
import com.ordo.itsm.audit.AuditLogService;
import com.ordo.itsm.global.exception.BusinessException;
import com.ordo.itsm.global.exception.ErrorCode;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.risk.RiskAssessment;
import com.ordo.itsm.risk.RiskAssessmentRepository;
import com.ordo.itsm.risk.RiskPolicy;
import com.ordo.itsm.risk.RiskPolicyService;
import com.ordo.itsm.sla.TicketSla;
import com.ordo.itsm.sla.TicketSlaRepository;
import com.ordo.itsm.tenant.TenantAccessService;
import com.ordo.itsm.ticket.RequestType;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriageService {

    /**
     * 트리아지 결과로 메타데이터·상태를 반영해도 되는 단계.
     * DRAFT 는 포함 — 메타데이터(분류·환경 등)만 채우고 상태 전환·SLA 정지는 submit 시점으로 미룬다.
     */
    private static final Set<TicketStatus> TRIAGEABLE = Set.of(
            TicketStatus.DRAFT,
            TicketStatus.SUBMITTED, TicketStatus.INFO_REQUIRED, TicketStatus.TRIAGED);

    private final TicketRepository ticketRepository;
    private final TicketSlaRepository ticketSlaRepository;
    private final AiTriageRecordRepository recordRepository;
    private final TenantAccessService tenantAccessService;
    private final SensitiveDataMasker masker;
    private final TriageEngine engine;
    private final TriageValidator validator;
    private final AuditLogService auditLogService;
    private final RiskPolicyService riskPolicyService;
    private final RiskAssessmentRepository riskAssessmentRepository;
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
            result = validator.validate(outcome.result(), masked);
        } catch (TriageEngineException e) {
            return recordFailure(user, ticket, attemptNo, e);
        }

        recordRepository.save(AiTriageRecord.of(ticket, attemptNo, engine.modelName(),
                TriageResultStatus.SUCCESS, jsonMapper.writeValueAsString(result), result.confidence()));

        boolean slaPaused = applyToTicket(user, ticket, result);
        RiskPolicy.Outcome risk = riskPolicyService.assess(user, ticket, result);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("attemptNo", attemptNo);
        after.put("model", engine.modelName());
        after.put("requestType", result.requestType().name());
        after.put("category", result.category());
        after.put("missingFields", result.missingFields());
        after.put("riskFactors", result.riskFactors());
        after.put("confidence", result.confidence());
        after.put("corrections", result.corrections() == null ? List.of() : result.corrections());
        after.put("ticketStatus", ticket.getStatus().name());
        auditLogService.record(AuditAction.AI_TRIAGED, user, ticket.getTenant().getId(), ticket.getId(), null, after);

        return new TriageResponse(attemptNo, TriageResultStatus.SUCCESS, engine.modelName(),
                result.confidence(), result.confidence() < TriageResponse.REVIEW_THRESHOLD,
                result, null, ticket.getStatus(), slaPaused || isSlaPaused(ticketId),
                risk == null ? null : risk.score(),
                risk == null ? null : risk.grade(),
                risk == null ? null : risk.factorPoints());
    }

    /**
     * 고객 보완(supplements)을 반영해 트리아지를 재실행한다. TicketService.supplement 로 저장 후 호출.
     * - validator 에 supplements 를 넘겨 원문 부정보다 우선해 "제공됨" 처리
     * - 위험도 재평가 (RISK_REASSESSED) + 상태 재적용 (INFO_REQUIRED → SUBMITTED + SLA 재개)
     */
    @Transactional
    public TriageResponse rerunWithSupplements(AuthUser user, Long ticketId, Map<String, String> supplements) {
        Ticket ticket = loadAccessible(user, ticketId);
        int attemptNo = recordRepository.countByTicket_Id(ticketId) + 1;

        String masked = masker.mask(ticket.getSubject() + "\n" + ticket.getOriginalRequest());

        TriageResult result;
        try {
            TriageOutcome outcome = engine.analyze(masked);
            result = validator.validate(outcome.result(), masked, supplements);
        } catch (TriageEngineException e) {
            return recordFailure(user, ticket, attemptNo, e);
        }

        recordRepository.save(AiTriageRecord.of(ticket, attemptNo, engine.modelName(),
                TriageResultStatus.SUCCESS, jsonMapper.writeValueAsString(result), result.confidence()));

        boolean slaPaused = applyToTicket(user, ticket, result);
        RiskPolicy.Outcome risk = riskPolicyService.assess(user, ticket, result);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("attemptNo", attemptNo);
        after.put("reason", "SUPPLEMENT_RERUN");
        after.put("supplementedFields", supplements.keySet());
        after.put("missingFields", result.missingFields());
        after.put("riskFactors", result.riskFactors());
        after.put("ticketStatus", ticket.getStatus().name());
        auditLogService.record(AuditAction.AI_TRIAGED, user, ticket.getTenant().getId(), ticket.getId(), null, after);

        return new TriageResponse(attemptNo, TriageResultStatus.SUCCESS, engine.modelName(),
                result.confidence(), result.confidence() < TriageResponse.REVIEW_THRESHOLD,
                result, null, ticket.getStatus(), slaPaused || isSlaPaused(ticketId),
                risk == null ? null : risk.score(),
                risk == null ? null : risk.grade(),
                risk == null ? null : risk.factorPoints());
    }

    /**
     * 저장된 최신 트리아지 결과(SUCCESS)를 지금 상태 티켓에 재적용한다.
     * DRAFT → SUBMITTED 로 접수한 직후, 누락 정보가 있으면 INFO_REQUIRED + SLA 정지가 반영되도록 호출.
     */
    @Transactional
    public void applyLatestToTicket(AuthUser user, Long ticketId) {
        Ticket ticket = loadAccessible(user, ticketId);
        AiTriageRecord record = recordRepository.findTopByTicket_IdOrderByAttemptNoDesc(ticketId).orElse(null);
        if (record == null || record.getResultStatus() != TriageResultStatus.SUCCESS || record.getResultJson() == null) {
            return;
        }
        TriageResult result = jsonMapper.readValue(record.getResultJson(), TriageResult.class);
        applyToTicket(user, ticket, result);
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

        // 저장된 current 평가가 있으면 그대로 돌려준다 (재계산하지 않음)
        RiskAssessment risk = riskAssessmentRepository.findByTicket_IdAndCurrentTrue(ticketId).orElse(null);
        Integer riskScore = risk == null ? null : risk.getScore();
        var riskGrade = risk == null ? null : risk.getGrade();
        Map<String, Integer> factorPoints = null;
        if (risk != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> raw = jsonMapper.readValue(risk.getFactorsJson(), Map.class);
                Map<String, Integer> parsed = new LinkedHashMap<>();
                for (var e : raw.entrySet()) parsed.put(e.getKey(), ((Number) e.getValue()).intValue());
                factorPoints = parsed;
            } catch (RuntimeException ignored) {
                factorPoints = null;
            }
        }

        return new TriageResponse(record.getAttemptNo(), record.getResultStatus(), record.getModelName(),
                confidence, confidence == null || confidence < TriageResponse.REVIEW_THRESHOLD,
                result, null, ticket.getStatus(), isSlaPaused(ticketId),
                riskScore, riskGrade, factorPoints);
    }

    private boolean applyToTicket(AuthUser user, Ticket ticket, TriageResult result) {
        if (!TRIAGEABLE.contains(ticket.getStatus())) {
            return false;
        }
        ticket.applyTriage(result.requestType(), result.category(), result.environment(), result.affectedService());

        // DRAFT 는 아직 접수 전 — 상태 전환·SLA 정지는 submit 시점에 applyLatestToTicket 로 적용한다.
        if (ticket.getStatus() == TicketStatus.DRAFT) {
            return false;
        }

        boolean hasMissing = !result.missingFields().isEmpty();
        // 장애(INCIDENT)는 누락 정보가 있어도 상태·SLA를 유지한다 (운영 대응 지연 방지).
        boolean skipTransition = result.requestType() == RequestType.INCIDENT;

        // 순방향: 누락 있음 → INFO_REQUIRED + SLA pause
        if (hasMissing && !skipTransition) {
            TicketStatus before = ticket.getStatus();
            if (before != TicketStatus.INFO_REQUIRED) {
                ticket.changeStatus(TicketStatus.INFO_REQUIRED);
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

        // 역방향: 누락 없음 + 현재 INFO_REQUIRED → SUBMITTED 로 복귀 + SLA 재개
        // 보완 완료 경로. 정지시간만큼 마감이 뒤로 밀린다.
        if (ticket.getStatus() == TicketStatus.INFO_REQUIRED) {
            ticket.changeStatus(TicketStatus.SUBMITTED);
            auditLogService.record(AuditAction.STATUS_CHANGED, user, ticket.getTenant().getId(), ticket.getId(),
                    Map.of("status", TicketStatus.INFO_REQUIRED.name()),
                    Map.of("status", TicketStatus.SUBMITTED.name(), "reason", "INFO_SUPPLEMENTED"));
            TicketSla sla = ticketSlaRepository.findByTicket_Id(ticket.getId()).orElse(null);
            if (sla != null && sla.isPaused()) {
                int minutes = sla.resume(OffsetDateTime.now());
                auditLogService.record(AuditAction.SLA_RESUMED, user, ticket.getTenant().getId(), ticket.getId(),
                        null, Map.of("resumedMinutes", minutes));
            }
            return false;
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
                ticket.getStatus(), isSlaPaused(ticket.getId()),
                null, null, null);
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
