package com.ordo.itsm.notification;

import com.ordo.itsm.user.User;
import com.ordo.itsm.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import jakarta.servlet.Filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 모니터링 시스템 계정(monitoring@{code}.demo) 은 사용 불가 해시로 생성되므로
 * 어떤 비밀번호로도 로그인되지 않아야 한다.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.user.name=disabled",
})
@Transactional
class MonitoringUserLoginIT {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository userRepository;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBeansOfType(Filter.class).values().toArray(new Filter[0]))
                .build();
    }

    @Test
    @DisplayName("모니터링 계정은 어떤 비밀번호로도 로그인 실패 (401)")
    void monitoringUser_cannotLogin() throws Exception {
        User monitor = userRepository.findByEmail("monitoring@a_univ.demo").orElseThrow(
                () -> new AssertionError("DemoDataInitializer 가 monitoring@a_univ.demo 를 만들었어야 함"));
        assertNotNull(monitor.getPasswordHash());
        assertNotEquals("Password123!", monitor.getPasswordHash(),
                "모니터링 계정은 데모 비밀번호가 아니라 사용 불가 해시여야 함");

        MockMvc mvc = mockMvc();
        // 데모 비밀번호·추측 가능한 값 어느 쪽으로도 로그인 성공 금지.
        // (빈 비밀번호는 validation 400 — 로그인 성공은 아니지만 이 테스트의 관심사와 다름이라 제외.)
        for (String password : new String[]{"Password123!", "monitoring", "monitoring@a_univ.demo"}) {
            var result = mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"monitoring@a_univ.demo\",\"password\":\"" + password + "\"}"))
                    .andReturn();
            assertEquals(401, result.getResponse().getStatus(),
                    "비밀번호 '" + password + "' 로 로그인 성공하면 안 됨 (실제 상태: " + result.getResponse().getStatus() + ")");
        }
    }
}
