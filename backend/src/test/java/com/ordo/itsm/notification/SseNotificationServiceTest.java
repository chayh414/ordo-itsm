package com.ordo.itsm.notification;

import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.tenant.TenantAccessService;
import com.ordo.itsm.tenant.TenantScope;
import com.ordo.itsm.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 범위 필터 (shouldReceive) 테스트. 사용자별 수신 규칙이 정확히 반영되는지 확인.
 * Spring 컨텍스트 없이 TenantAccessService 만 mock 으로 주입해서 pure JUnit 으로 돌린다.
 */
class SseNotificationServiceTest {

    private static final Long A_UNIV = 10L;
    private static final Long B_BANK = 20L;
    private static final Long C_MALL = 30L;

    private NotificationEvent incidentInA(Long requesterId) {
        return new NotificationEvent(
                NotificationEvent.Type.INCIDENT_CREATED,
                100L, "ORD-x",
                A_UNIV, "A대학교",
                requesterId,
                "수강신청 사이트", "502 오류",
                OffsetDateTime.now().plusMinutes(15),
                OffsetDateTime.now());
    }

    private NotificationEvent slaNearInA(Long requesterId) {
        return new NotificationEvent(
                NotificationEvent.Type.SLA_NEAR,
                200L, "ORD-y",
                A_UNIV, "A대학교",
                requesterId,
                "운영 DB 컬럼 추가", "마감 15분 남음",
                OffsetDateTime.now().plusMinutes(15),
                OffsetDateTime.now());
    }

    private SseNotificationService svc() {
        TenantAccessService tenantAccess = mock(TenantAccessService.class);
        // operator@sysone: A대학교·B은행 담당
        when(tenantAccess.scopeOf(opUser())).thenReturn(TenantScope.of(Set.of(A_UNIV, B_BANK)));
        return new SseNotificationService(tenantAccess);
    }

    private AuthUser opsAdmin() { return new AuthUser(1L, 100L, UserRole.OPS_ADMIN, "admin@sysone.demo"); }
    private AuthUser opUser() { return new AuthUser(2L, 100L, UserRole.OPERATOR, "op@sysone.demo"); }
    private AuthUser aRequester() { return new AuthUser(3L, A_UNIV, UserRole.REQUESTER, "r@a-univ.demo"); }
    private AuthUser bRequester() { return new AuthUser(4L, B_BANK, UserRole.REQUESTER, "r@b-bank.demo"); }

    @Test
    @DisplayName("INCIDENT: 관리자는 모든 테넌트 수신")
    void opsAdmin_receivesAll() {
        assertTrue(svc().shouldReceive(opsAdmin(), incidentInA(3L)));
    }

    @Test
    @DisplayName("INCIDENT: 운영 담당자는 담당 테넌트만")
    void operator_receivesOnlyAssignedTenants() {
        var s = svc();
        assertTrue(s.shouldReceive(opUser(), incidentInA(3L)), "A대학교 담당 → 수신");
        NotificationEvent inCMall = new NotificationEvent(
                NotificationEvent.Type.INCIDENT_CREATED, 101L, "ORD-c",
                C_MALL, "C쇼핑몰", 9L, "주문", "오류",
                OffsetDateTime.now(), OffsetDateTime.now());
        assertFalse(s.shouldReceive(opUser(), inCMall), "C쇼핑몰 담당 아님 → 미수신");
    }

    @Test
    @DisplayName("INCIDENT: 요청자는 자기 테넌트 장애만 (다른 테넌트 안 뜸)")
    void requester_receivesOnlyOwnTenant_forIncident() {
        var s = svc();
        assertTrue(s.shouldReceive(aRequester(), incidentInA(999L)),
                "A대학교 요청자 → A대학교 장애 수신 (requesterId 와 무관)");
        assertFalse(s.shouldReceive(bRequester(), incidentInA(999L)),
                "B은행 요청자 → A대학교 장애 안 뜸");
    }

    @Test
    @DisplayName("SLA_NEAR: 요청자는 자기 티켓만 (같은 테넌트라도 다른 사람 요청은 안 뜸)")
    void requester_receivesOnlyOwnTicket_forSlaNear() {
        var s = svc();
        AuthUser alice = aRequester();   // userId=3L
        assertTrue(s.shouldReceive(alice, slaNearInA(3L)),
                "자기 요청(requesterId=3) → 수신");
        assertFalse(s.shouldReceive(alice, slaNearInA(99L)),
                "같은 테넌트지만 다른 사람 요청(requesterId=99) → 미수신");
    }

    @Test
    @DisplayName("SLA_NEAR: 운영 담당자는 담당 테넌트 전체 (요청자 상관없이)")
    void operator_receivesAllTicketsInScope_forSlaNear() {
        var s = svc();
        assertTrue(s.shouldReceive(opUser(), slaNearInA(999L)),
                "A대학교 담당 → requesterId 와 무관하게 수신");
    }
}
