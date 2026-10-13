package com.ordo.itsm.notification;

import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.tenant.TenantAccessService;
import com.ordo.itsm.tenant.TenantScope;
import com.ordo.itsm.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 실시간 알림을 SSE 로 전송. 사용자별 접속을 메모리에 보관하고 broadcast 시 범위 필터를 적용한다.
 *
 * - 사용자당 여러 탭(=여러 SseEmitter) 지원
 * - 범위 필터 (shouldReceive):
 *     INCIDENT_CREATED  : OPS_ADMIN·APPROVER = 전체, OPERATOR·CUSTOMER_ADMIN = TenantScope, REQUESTER = 자기 테넌트
 *     SLA_NEAR          : REQUESTER 만 추가로 "자기 티켓(requesterId == userId)" 조건
 * - heartbeat: 10 초마다 keepalive comment 를 전송해 중간 프록시가 끊지 않게 함
 * - 재연결은 클라이언트(fetch-event-source) 가 자동. 서버는 끊긴 emitter 를 등록 해제만.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SseNotificationService {

    /** 접속 수명 (서버가 명시적으로 자르는 상한). fetch-event-source 는 끊기면 자동 재연결. */
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;    // 30분

    private final TenantAccessService tenantAccessService;

    /** userId → (user, 열려있는 SseEmitter 들) */
    private final Map<Long, Subscription> subscriptions = new ConcurrentHashMap<>();

    public SseEmitter register(AuthUser user) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        Subscription sub = subscriptions.computeIfAbsent(user.userId(), id -> new Subscription(user));
        sub.emitters.add(emitter);

        emitter.onCompletion(() -> remove(user.userId(), emitter));
        emitter.onTimeout(() -> remove(user.userId(), emitter));
        emitter.onError(e -> remove(user.userId(), emitter));

        try {
            // 초기 핸드셰이크 — fetch-event-source 가 open 을 인식하도록 comment 전송.
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            remove(user.userId(), emitter);
        }
        log.debug("[SSE] 연결 user={} role={} 활성={}", user.userId(), user.role(), countOpen());
        return emitter;
    }

    public void broadcast(NotificationEvent event) {
        int sent = 0;
        for (Subscription sub : subscriptions.values()) {
            if (!shouldReceive(sub.user, event)) continue;
            for (SseEmitter emitter : List.copyOf(sub.emitters)) {
                try {
                    emitter.send(SseEmitter.event().name("notification").data(event));
                    sent++;
                } catch (IOException e) {
                    remove(sub.user.userId(), emitter);
                }
            }
        }
        log.info("[SSE] broadcast type={} ticketNo={} 전송={}", event.type(), event.ticketNo(), sent);
    }

    /**
     * 범위 필터. 서버에서 결정 — 프론트는 받은 알림을 그대로 믿는다.
     */
    boolean shouldReceive(AuthUser user, NotificationEvent event) {
        UserRole role = user.role();
        // 공급사 측 (서비스데스크·승인자·관리자) — 테넌트 범위 안이면 수신
        if (role == UserRole.OPS_ADMIN || role == UserRole.APPROVER) {
            // SLA_NEAR 는 전체, INCIDENT 도 전체
            return true;
        }
        if (role == UserRole.OPERATOR) {
            TenantScope scope = tenantAccessService.scopeOf(user);
            if (!scope.includes(event.tenantId())) return false;
            return true;
        }
        // 고객사 측
        if (!user.tenantId().equals(event.tenantId())) return false;

        if (role == UserRole.CUSTOMER_ADMIN) {
            // 자기 테넌트 전체
            return true;
        }
        if (role == UserRole.REQUESTER) {
            // INCIDENT: 자기 테넌트 장애 전부 수신
            // SLA_NEAR: 자기 티켓만
            if (event.type() == NotificationEvent.Type.SLA_NEAR) {
                return event.requesterId() != null && event.requesterId().equals(user.userId());
            }
            return true;
        }
        return false;
    }

    /** 10 초마다 keepalive comment 전송. 중간 프록시가 idle 로 끊지 않게 유지. */
    @Scheduled(fixedDelay = 10_000L)
    public void heartbeat() {
        for (Subscription sub : subscriptions.values()) {
            for (SseEmitter emitter : List.copyOf(sub.emitters)) {
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (IOException e) {
                    remove(sub.user.userId(), emitter);
                }
            }
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        Subscription sub = subscriptions.get(userId);
        if (sub == null) return;
        sub.emitters.remove(emitter);
        if (sub.emitters.isEmpty()) {
            subscriptions.remove(userId, sub);
        }
    }

    private int countOpen() {
        return subscriptions.values().stream().mapToInt(s -> s.emitters.size()).sum();
    }

    /** 사용자 1인의 활성 접속 묶음. */
    private static final class Subscription {
        final AuthUser user;
        final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

        Subscription(AuthUser user) {
            this.user = user;
        }
    }
}
