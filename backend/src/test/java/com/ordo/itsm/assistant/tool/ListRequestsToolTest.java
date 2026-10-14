package com.ordo.itsm.assistant.tool;

import com.ordo.itsm.global.common.PageResponse;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.tenant.Tenant;
import com.ordo.itsm.tenant.TenantAccessService;
import com.ordo.itsm.tenant.TenantRepository;
import com.ordo.itsm.tenant.TenantScope;
import com.ordo.itsm.ticket.TicketService;
import com.ordo.itsm.ticket.TicketStatus;
import com.ordo.itsm.ticket.dto.TicketSummaryResponse;
import com.ordo.itsm.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ListRequestsTool 의 tenant 인자 처리.
 * - 범위 안: 그 테넌트로 좁혀 ticketService.listWithinScope 호출
 * - 범위 밖: 조회 생략, 안내 메시지만
 */
class ListRequestsToolTest {

    private TicketService ticketService;
    private TenantRepository tenantRepository;
    private TenantAccessService tenantAccessService;
    private ListRequestsTool tool;

    private final AuthUser operator = new AuthUser(2L, 100L, UserRole.OPERATOR, "op@sysone.demo");

    @BeforeEach
    void setUp() {
        ticketService = mock(TicketService.class);
        tenantRepository = mock(TenantRepository.class);
        tenantAccessService = mock(TenantAccessService.class);
        tool = new ListRequestsTool(ticketService, tenantRepository, tenantAccessService);

        // doReturn 패턴 — when().thenReturn() 중간 mock 호출로 Mockito 가 unfinished stubbing 로 오해하는 걸 피함.
        doReturn(TenantScope.of(Set.of(10L, 20L))).when(tenantAccessService).scopeOf(any());
    }

    @Test
    @DisplayName("범위 안 고객사 지정 — 그 테넌트로 좁혀 조회")
    void withinScope_narrowsToTenant() {
        Tenant aUniv = mock(Tenant.class);
        doReturn(10L).when(aUniv).getId();
        doReturn("A대학교").when(aUniv).getName();
        doReturn(Optional.of(aUniv)).when(tenantRepository).findByCodeOrName("A대학교");
        doReturn(PageResponse.empty(org.springframework.data.domain.PageRequest.of(0, 10)))
                .when(ticketService).listWithinScope(any(), any(TenantScope.class), any(), any());

        ToolResult result = tool.execute(operator, Map.of("tenant", "A대학교"));

        assertTrue(result.ok());
        assertTrue(result.llmText().contains("A대학교"),
                "llmText 첫 줄 '조건' 에 고객사 이름 포함: " + result.llmText());
        verify(ticketService).listWithinScope(eq(operator), any(TenantScope.class),
                eq((TicketStatus) null), any());
    }

    @Test
    @DisplayName("범위 밖 고객사 — 조회 안 하고 안내만")
    void outsideScope_returnsGuidance() {
        Tenant cMall = mock(Tenant.class);
        doReturn(30L).when(cMall).getId();     // 담당 아님
        doReturn(Optional.of(cMall)).when(tenantRepository).findByCodeOrName("C쇼핑몰");

        ToolResult result = tool.execute(operator, Map.of("tenant", "C쇼핑몰"));

        assertTrue(result.ok());
        assertTrue(result.llmText().contains("담당 고객사가 아닙니다"),
                "안내 포함: " + result.llmText());
        verify(ticketService, never()).listWithinScope(any(), any(), any(), any());
    }

    @Test
    @DisplayName("tenant 생략 — 사용자 전체 scope 로 조회")
    void withoutTenant_usesUserScope() {
        doReturn(PageResponse.empty(org.springframework.data.domain.PageRequest.of(0, 10)))
                .when(ticketService).listWithinScope(any(), any(TenantScope.class), any(), any());

        ToolResult result = tool.execute(operator, Map.of());

        assertTrue(result.ok());
        assertTrue(result.llmText().startsWith("조건: 전체 고객사"),
                "tenant 생략 시 첫 줄 '전체 고객사': " + result.llmText());
        verify(ticketService).listWithinScope(eq(operator), any(TenantScope.class), any(), any());
    }

    @Test
    @DisplayName("한글 라벨 status 수용 — '보완 대기' → INFO_REQUIRED")
    void acceptsKoreanStatusLabel() {
        doReturn(PageResponse.empty(org.springframework.data.domain.PageRequest.of(0, 10)))
                .when(ticketService).listWithinScope(any(), any(TenantScope.class),
                        eq(TicketStatus.INFO_REQUIRED), any());

        ToolResult result = tool.execute(operator, Map.of("status", "보완 대기"));

        assertTrue(result.ok());
        verify(ticketService).listWithinScope(eq(operator), any(TenantScope.class),
                eq(TicketStatus.INFO_REQUIRED), any());
        assertTrue(result.llmText().contains("보완 대기"),
                "조건 라벨 '보완 대기': " + result.llmText());
    }
}
