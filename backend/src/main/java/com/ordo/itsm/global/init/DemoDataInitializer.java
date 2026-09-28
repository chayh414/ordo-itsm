package com.ordo.itsm.global.init;

import com.ordo.itsm.tenant.Tenant;
import com.ordo.itsm.tenant.TenantRepository;
import com.ordo.itsm.user.User;
import com.ordo.itsm.user.UserRepository;
import com.ordo.itsm.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연용 계정 생성. users 테이블이 비어 있을 때 한 번만 실행된다.
 * 모든 계정 비밀번호: Password123!
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "Password123!";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        Tenant sysone = tenant("SYSONE");
        Tenant aUniv = tenant("A-UNIV");
        Tenant bBank = tenant("B-BANK");

        String hash = passwordEncoder.encode(DEMO_PASSWORD);

        // 운영사 (시스원)
        userRepository.save(User.create(sysone, "admin@sysone.demo", hash, "박관리", UserRole.OPS_ADMIN));
        User operator = userRepository.save(User.create(sysone, "operator@sysone.demo", hash, "이운영", UserRole.OPERATOR));
        User approver = userRepository.save(User.create(sysone, "approver@sysone.demo", hash, "최승인", UserRole.APPROVER));

        // 고객사
        userRepository.save(User.create(aUniv, "requester@a-univ.demo", hash, "김요청", UserRole.REQUESTER));
        userRepository.save(User.create(aUniv, "admin@a-univ.demo", hash, "정학사", UserRole.CUSTOMER_ADMIN));
        userRepository.save(User.create(bBank, "requester@b-bank.demo", hash, "한은행", UserRole.REQUESTER));
        userRepository.flush();

        // 운영 담당자·승인자는 A대학교, B은행만 담당 (C쇼핑몰은 접근 불가 → 권한 시연용)
        for (Tenant customer : new Tenant[]{aUniv, bBank}) {
            grantAccess(operator, customer, "OPERATOR");
            grantAccess(approver, customer, "APPROVER");
        }

        log.info("[Ordo] 시연용 계정 6개 생성 완료 (비밀번호: {})", DEMO_PASSWORD);
    }

    private Tenant tenant(String code) {
        return tenantRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("테넌트 시드 데이터 없음: " + code));
    }

    private void grantAccess(User user, Tenant tenant, String accessRole) {
        jdbcTemplate.update(
                "INSERT INTO user_tenant_access (user_id, tenant_id, access_role) VALUES (?, ?, ?)",
                user.getId(), tenant.getId(), accessRole
        );
    }
}
