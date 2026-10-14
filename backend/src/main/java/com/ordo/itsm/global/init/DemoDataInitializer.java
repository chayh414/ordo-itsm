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
        if (userRepository.count() == 0) {
            seedInitial();
        }
        ensureMonitoringUsers();
    }

    private void seedInitial() {
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

        // 운영 담당자·승인자는 A제조, B은행만 담당 (C쇼핑몰은 접근 불가 → 권한 시연용)
        for (Tenant customer : new Tenant[]{aUniv, bBank}) {
            grantAccess(operator, customer, "OPERATOR");
            grantAccess(approver, customer, "APPROVER");
        }

        log.info("[Ordo] 시연용 계정 6개 생성 완료 (비밀번호: {})", DEMO_PASSWORD);
    }

    /**
     * 모니터링 이벤트 수신용 시스템 계정을 테넌트별로 보장한다.
     * - IncidentDemoController 가 장애 요청의 requester 로 사용.
     * - 로그인 불가 (랜덤 UUID 를 bcrypt 로 인코딩 — 사실상 매치되는 raw 가 없음).
     * - 실제 운영에선 SYSTEM 역할로 분리 예정.
     * 이미 DB 에 시드가 있어 seedInitial 을 skip 하는 재시작 상황에서도 반드시 추가되도록 분리.
     */
    private void ensureMonitoringUsers() {
        String unusableHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
        for (String tenantCode : new String[]{"A-UNIV", "B-BANK"}) {
            String email = "monitoring@" + tenantCode.toLowerCase().replace("-", "_") + ".demo";
            if (userRepository.findByEmail(email).isPresent()) continue;
            Tenant t = tenant(tenantCode);
            userRepository.save(User.create(t, email, unusableHash,
                    "모니터링 이벤트 (AiWACS 연동 예정)", UserRole.REQUESTER));
            log.info("[Ordo] 모니터링 시스템 계정 생성: {}", email);
        }
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
