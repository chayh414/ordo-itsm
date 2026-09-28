package com.ordo.itsm.tenant;

import com.ordo.itsm.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/** 역할별 고객사 접근 범위 계산 (기획안 6.2) */
@Service
@RequiredArgsConstructor
public class TenantAccessService {

    private final JdbcTemplate jdbcTemplate;

    public TenantScope scopeOf(AuthUser user) {
        return switch (user.role()) {
            case OPS_ADMIN -> TenantScope.everything();
            case REQUESTER, CUSTOMER_ADMIN -> TenantScope.of(Set.of(user.tenantId()));
            case OPERATOR, APPROVER -> TenantScope.of(new HashSet<>(jdbcTemplate.queryForList(
                    "SELECT tenant_id FROM user_tenant_access WHERE user_id = ?",
                    Long.class,
                    user.userId()
            )));
        };
    }
}
