package com.ordo.itsm.audit;

import com.ordo.itsm.global.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final JsonMapper jsonMapper;

    /** 현재 작업과 같은 트랜잭션에 기록 (작업이 롤백되면 로그도 롤백) */
    @Transactional
    public void record(AuditAction action, AuthUser actor, Long tenantId, Long ticketId,
                       Map<String, ?> before, Map<String, ?> after) {
        write(action, actor, tenantId, ticketId, before, after);
    }

    /**
     * 작업이 실패·거부되어도 반드시 남겨야 하는 로그 (접근 거부 등).
     * 별도 트랜잭션으로 즉시 커밋한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependently(AuditAction action, AuthUser actor, Long tenantId, Long ticketId,
                                    Map<String, ?> before, Map<String, ?> after) {
        write(action, actor, tenantId, ticketId, before, after);
    }

    private void write(AuditAction action, AuthUser actor, Long tenantId, Long ticketId,
                       Map<String, ?> before, Map<String, ?> after) {
        auditLogRepository.save(AuditLog.of(
                tenantId,
                actor != null ? actor.userId() : null,
                ticketId,
                action,
                toJson(before),
                toJson(after),
                currentIp()
        ));
    }

    private String toJson(Map<String, ?> value) {
        return value == null ? null : jsonMapper.writeValueAsString(value);
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
