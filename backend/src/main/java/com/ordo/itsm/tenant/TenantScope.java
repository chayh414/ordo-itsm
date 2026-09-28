package com.ordo.itsm.tenant;

import java.util.Set;

/**
 * 로그인 사용자가 조회할 수 있는 고객사 범위.
 * all=true 이면 전체 고객사(운영 관리자), 아니면 tenantIds에 포함된 고객사만.
 */
public record TenantScope(boolean all, Set<Long> tenantIds) {

    public static TenantScope everything() {
        return new TenantScope(true, Set.of());
    }

    public static TenantScope of(Set<Long> tenantIds) {
        return new TenantScope(false, Set.copyOf(tenantIds));
    }

    public boolean includes(Long tenantId) {
        return all || tenantIds.contains(tenantId);
    }

    public boolean isEmpty() {
        return !all && tenantIds.isEmpty();
    }
}
