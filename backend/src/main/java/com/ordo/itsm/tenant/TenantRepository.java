package com.ordo.itsm.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByCode(String code);

    /**
     * 코드 또는 이름 완전 일치. 어시스턴트가 "A대학교" / "A-UNIV" 어느 쪽으로 넘겨도 매칭.
     */
    @Query("select t from Tenant t where t.code = :key or t.name = :key")
    Optional<Tenant> findByCodeOrName(@Param("key") String key);
}
