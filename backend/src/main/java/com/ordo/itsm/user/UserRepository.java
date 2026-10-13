package com.ordo.itsm.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    /** 시연용 장애 접수에서 "그 테넌트의 아무 요청자" 를 requester 로 지정할 때 사용. */
    Optional<User> findFirstByTenant_IdAndRole(Long tenantId, UserRole role);
}
