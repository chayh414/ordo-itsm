package com.ordo.itsm.global.security;

import com.ordo.itsm.user.UserRole;

/**
 * JWT에서 꺼낸 로그인 사용자 정보.
 * 컨트롤러에서 @AuthenticationPrincipal AuthUser authUser 로 받는다.
 * tenantId는 클라이언트 입력이 아니라 반드시 여기서 꺼내 사용한다.
 */
public record AuthUser(Long userId, Long tenantId, UserRole role, String email) {

    public boolean isProviderSide() {
        return role == UserRole.OPERATOR || role == UserRole.APPROVER || role == UserRole.OPS_ADMIN;
    }
}
