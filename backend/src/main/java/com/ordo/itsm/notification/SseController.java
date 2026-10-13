package com.ordo.itsm.notification;

import com.ordo.itsm.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 실시간 알림 SSE 엔드포인트.
 *
 * 인증: 기존 JWT 필터 그대로. 프론트는 fetch-event-source 로 Authorization 헤더를 그대로 보낸다
 * (EventSource 는 커스텀 헤더를 못 넣기 때문). 401 은 전역 핸들러에서 로그인으로 리다이렉트.
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class SseController {

    private final SseNotificationService sseService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthUser user) {
        return sseService.register(user);
    }
}
