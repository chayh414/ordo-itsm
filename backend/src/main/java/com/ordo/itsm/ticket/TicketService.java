package com.ordo.itsm.ticket;

import com.ordo.itsm.audit.AuditAction;
import com.ordo.itsm.audit.AuditLogService;
import com.ordo.itsm.global.common.PageResponse;
import com.ordo.itsm.global.exception.BusinessException;
import com.ordo.itsm.global.exception.ErrorCode;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.sla.SlaResponse;
import com.ordo.itsm.sla.SlaService;
import com.ordo.itsm.sla.TicketSla;
import com.ordo.itsm.sla.TicketSlaRepository;
import com.ordo.itsm.tenant.Tenant;
import com.ordo.itsm.tenant.TenantAccessService;
import com.ordo.itsm.tenant.TenantRepository;
import com.ordo.itsm.tenant.TenantScope;
import com.ordo.itsm.ticket.dto.CreateTicketRequest;
import com.ordo.itsm.ticket.dto.TicketCreatedResponse;
import com.ordo.itsm.ticket.dto.TicketDetailResponse;
import com.ordo.itsm.ticket.dto.TicketSummaryResponse;
import com.ordo.itsm.user.User;
import com.ordo.itsm.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TicketNumberGenerator ticketNumberGenerator;
    private final TenantAccessService tenantAccessService;
    private final SlaService slaService;
    private final TicketSlaRepository ticketSlaRepository;
    private final AuditLogService auditLogService;
    private final JsonMapper jsonMapper;

    /** 요청 등록: 원문 저장 → 티켓번호 발급 → SLA 시작 → 감사 로그 (직통 접수) */
    @Transactional
    public TicketCreatedResponse create(AuthUser user, CreateTicketRequest request) {
        if (user.isProviderSide()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "요청 등록은 고객사 사용자만 할 수 있습니다.");
        }

        Ticket ticket = ticketRepository.save(Ticket.create(
                ticketNumberGenerator.next(),
                tenantRepository.getReferenceById(user.tenantId()),
                userRepository.getReferenceById(user.userId()),
                request.subject().trim(),
                request.originalRequest().trim()
        ));

        TicketSla sla = slaService.start(ticket);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("ticketNo", ticket.getTicketNo());
        after.put("subject", ticket.getSubject());
        after.put("status", ticket.getStatus().name());
        after.put("slaResolutionDueAt", sla.getResolutionDueAt().toString());
        auditLogService.record(AuditAction.TICKET_CREATED, user, user.tenantId(), ticket.getId(), null, after);

        return new TicketCreatedResponse(
                ticket.getId(), ticket.getTicketNo(), ticket.getStatus(),
                SlaResponse.from(sla), null, ticket.getCreatedAt());
    }

    /**
     * 어시스턴트 경로 — LLM 판단만으로 티켓을 임시(DRAFT) 생성한다.
     * SLA 는 시작하지 않고, 트리아지(별도 호출)로 메타데이터만 채운다.
     * 사용자가 카드의 [접수하기] 로 submit() 를 호출해야 실제 접수가 된다.
     */
    @Transactional
    public TicketCreatedResponse createDraft(AuthUser user, CreateTicketRequest request) {
        if (user.isProviderSide()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "요청 등록은 고객사 사용자만 할 수 있습니다.");
        }

        Ticket ticket = ticketRepository.save(Ticket.createDraft(
                ticketNumberGenerator.next(),
                tenantRepository.getReferenceById(user.tenantId()),
                userRepository.getReferenceById(user.userId()),
                request.subject().trim(),
                request.originalRequest().trim()
        ));

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("ticketNo", ticket.getTicketNo());
        after.put("subject", ticket.getSubject());
        after.put("status", ticket.getStatus().name());
        after.put("source", "ASSISTANT_DRAFT");
        auditLogService.record(AuditAction.TICKET_CREATED, user, user.tenantId(), ticket.getId(), null, after);

        return new TicketCreatedResponse(
                ticket.getId(), ticket.getTicketNo(), ticket.getStatus(),
                null, null, ticket.getCreatedAt());
    }

    /**
     * "확인이 필요한 내용" 질문에 대한 사용자 답변을 tickets.detail_json 의 supplements 에 저장.
     * - 요청자 본인만, DRAFT 또는 INFO_REQUIRED 상태일 때만
     * - 재분류·위험도 재평가는 호출자가 TriageService.rerunWithSupplements 로 이어 실행
     * 감사: INFO_SUPPLEMENTED
     */
    @Transactional
    public void supplement(AuthUser user, Long ticketId, String field, String answer) {
        if (field == null || field.isBlank() || answer == null || answer.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "field 와 answer 가 모두 필요합니다.");
        }
        Ticket ticket = ticketRepository.findWithTenantAndRequesterById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));

        if (!tenantAccessService.scopeOf(user).includes(ticket.getTenant().getId())
                || !ticket.getRequester().getId().equals(user.userId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "본인이 올린 요청만 보완할 수 있습니다.");
        }
        if (ticket.getStatus() != TicketStatus.DRAFT && ticket.getStatus() != TicketStatus.INFO_REQUIRED) {
            throw new BusinessException(ErrorCode.TICKET_INVALID_TRANSITION,
                    "지금 상태(" + ticket.getStatus() + ")에서는 보완할 수 없습니다.");
        }

        Map<String, Object> detail = parseDetails(ticket.getDetailJson());
        Map<String, Object> mutable = new LinkedHashMap<>(detail);
        @SuppressWarnings("unchecked")
        Map<String, String> sups = (Map<String, String>) mutable.get("supplements");
        if (sups == null) sups = new LinkedHashMap<>();
        else sups = new LinkedHashMap<>(sups);
        sups.put(field.trim(), answer.trim());
        mutable.put("supplements", sups);
        ticket.applyDetailJson(jsonMapper.writeValueAsString(mutable));

        auditLogService.record(AuditAction.INFO_SUPPLEMENTED, user, ticket.getTenant().getId(), ticket.getId(),
                null, Map.of("field", field.trim(), "answer", answer.trim()));
    }

    /** TriageService.rerunWithSupplements 가 사용할 — 티켓의 detail_json 에서 보완 맵 추출. */
    public Map<String, String> getSupplements(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
        Map<String, Object> detail = parseDetails(ticket.getDetailJson());
        @SuppressWarnings("unchecked")
        Map<String, String> sups = (Map<String, String>) detail.get("supplements");
        return sups == null ? Map.of() : Map.copyOf(sups);
    }

    /**
     * DRAFT 티켓을 사용자가 직접 접수한다.
     * - 요청자 본인만 가능
     * - DRAFT 상태에서만 가능 (idempotent 아님: 이미 SUBMITTED 면 409)
     * - SLA 를 시작하고 상태를 SUBMITTED 로 전환. 트리아지의 INFO_REQUIRED 전환은 호출자가 재적용.
     */
    @Transactional
    public void submit(AuthUser user, Long ticketId) {
        Ticket ticket = ticketRepository.findWithTenantAndRequesterById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));

        if (!tenantAccessService.scopeOf(user).includes(ticket.getTenant().getId())
                || !ticket.getRequester().getId().equals(user.userId())) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("reason", "NOT_OWNER");
            detail.put("targetTicketNo", ticket.getTicketNo());
            auditLogService.recordIndependently(AuditAction.ACCESS_DENIED, user, user.tenantId(), ticket.getId(), null, detail);
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "본인이 작성한 초안만 접수할 수 있습니다.");
        }

        if (ticket.getStatus() != TicketStatus.DRAFT) {
            throw new BusinessException(ErrorCode.TICKET_INVALID_TRANSITION,
                    "이미 접수된 요청입니다. (현재 상태: " + ticket.getStatus() + ")");
        }

        TicketStatus before = ticket.getStatus();
        ticket.changeStatus(TicketStatus.SUBMITTED);
        TicketSla sla = slaService.start(ticket);

        auditLogService.record(AuditAction.STATUS_CHANGED, user, ticket.getTenant().getId(), ticket.getId(),
                Map.of("status", before.name()),
                Map.of("status", TicketStatus.SUBMITTED.name(),
                        "reason", "USER_CONFIRMED",
                        "slaResolutionDueAt", sla.getResolutionDueAt().toString()));
    }

    /**
     * 목록: 접근 범위 안에서만 + DRAFT 가시성 규칙 적용.
     * DRAFT 는 작성자 본인에게만 보이고, 그 외 사용자(같은 테넌트 포함)에게는 숨긴다.
     */
    public PageResponse<TicketSummaryResponse> list(AuthUser user, TicketStatus status, Pageable pageable) {
        TenantScope scope = tenantAccessService.scopeOf(user);
        if (scope.isEmpty()) {
            return PageResponse.empty(pageable);
        }

        TicketStatus draft = TicketStatus.DRAFT;
        Long userId = user.userId();
        Page<Ticket> page;
        if (scope.all()) {
            page = (status == null)
                    ? ticketRepository.findAllVisible(draft, userId, pageable)
                    : ticketRepository.findByStatusVisible(status, draft, userId, pageable);
        } else {
            page = (status == null)
                    ? ticketRepository.findByTenantInVisible(scope.tenantIds(), draft, userId, pageable)
                    : ticketRepository.findByTenantInAndStatusVisible(scope.tenantIds(), status, draft, userId, pageable);
        }

        List<Long> ids = page.getContent().stream().map(Ticket::getId).toList();
        Map<Long, TicketSla> slaByTicketId = ids.isEmpty()
                ? Map.of()
                : ticketSlaRepository.findByTicket_IdIn(ids).stream()
                        .collect(Collectors.toMap(s -> s.getTicket().getId(), Function.identity()));

        return PageResponse.from(page.map(t -> TicketSummaryResponse.from(t, slaByTicketId.get(t.getId()))));
    }

    /**
     * 어시스턴트 "내 요청" 전용 조회. REQUESTER 는 본인 작성 티켓만, CUSTOMER_ADMIN 은 자기 테넌트 전체(가시성 필터 적용).
     * 그 외 역할은 호출되지 않는다 (dispatcher 가 미리 걸러줌). 호출되면 500.
     */
    public PageResponse<TicketSummaryResponse> listMine(AuthUser user, TicketStatus status, Pageable pageable) {
        return switch (user.role()) {
            case REQUESTER -> {
                Page<Ticket> page = (status == null)
                        ? ticketRepository.findByRequester_Id(user.userId(), pageable)
                        : ticketRepository.findByRequester_IdAndStatus(user.userId(), status, pageable);
                yield mapToSummaryPage(page);
            }
            case CUSTOMER_ADMIN -> list(user, status, pageable);
            default -> throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "listMine 은 요청자/고객사 관리자 전용입니다.");
        };
    }

    private PageResponse<TicketSummaryResponse> mapToSummaryPage(Page<Ticket> page) {
        List<Long> ids = page.getContent().stream().map(Ticket::getId).toList();
        Map<Long, TicketSla> slaByTicketId = ids.isEmpty()
                ? Map.of()
                : ticketSlaRepository.findByTicket_IdIn(ids).stream()
                        .collect(Collectors.toMap(s -> s.getTicket().getId(), Function.identity()));
        return PageResponse.from(page.map(t -> TicketSummaryResponse.from(t, slaByTicketId.get(t.getId()))));
    }

    /** 티켓번호로 상세 조회 — 어시스턴트의 get_request 도구에서 사용. */
    public TicketDetailResponse getByTicketNo(AuthUser user, String ticketNo) {
        Ticket ticket = ticketRepository.findByTicketNo(ticketNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
        return get(user, ticket.getId());
    }

    /** 상세: 다른 고객사 티켓이면 403 + 접근 거부 감사 로그 */
    public TicketDetailResponse get(AuthUser user, Long ticketId) {
        Ticket ticket = ticketRepository.findWithTenantAndRequesterById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));

        Long ticketTenantId = ticket.getTenant().getId();
        if (!tenantAccessService.scopeOf(user).includes(ticketTenantId)) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("reason", "TENANT_MISMATCH");
            detail.put("targetTicketNo", ticket.getTicketNo());
            detail.put("targetTenantId", ticketTenantId);
            auditLogService.recordIndependently(AuditAction.ACCESS_DENIED, user, user.tenantId(), ticket.getId(), null, detail);

            throw new BusinessException(ErrorCode.ACCESS_DENIED, "다른 고객사의 티켓에는 접근할 수 없습니다.");
        }

        TicketSla sla = ticketSlaRepository.findByTicket_Id(ticketId).orElse(null);
        return TicketDetailResponse.from(ticket, sla, parseDetails(ticket.getDetailJson()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseDetails(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return jsonMapper.readValue(json, Map.class);
    }
}
