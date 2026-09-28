package com.ordo.itsm.auth.dto;

import com.ordo.itsm.tenant.Tenant;
import com.ordo.itsm.user.User;
import com.ordo.itsm.user.UserRole;

public record MeResponse(Long id, String email, String name, UserRole role, TenantSummary tenant) {

    public record TenantSummary(Long id, String code, String name) {
    }

    public static MeResponse from(User user) {
        Tenant t = user.getTenant();
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                new TenantSummary(t.getId(), t.getCode(), t.getName())
        );
    }
}
