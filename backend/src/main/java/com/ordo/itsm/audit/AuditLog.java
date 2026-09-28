package com.ordo.itsm.audit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/** 감사 로그. INSERT만 허용 (DB 트리거로 UPDATE·DELETE 차단) */
@Entity
@Immutable
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(nullable = false, length = 50)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json")
    private String beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json")
    private String afterJson;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static AuditLog of(Long tenantId, Long actorId, Long ticketId, AuditAction action,
                              String beforeJson, String afterJson, String ipAddress) {
        AuditLog log = new AuditLog();
        log.tenantId = tenantId;
        log.actorId = actorId;
        log.ticketId = ticketId;
        log.action = action.name();
        log.beforeJson = beforeJson;
        log.afterJson = afterJson;
        log.ipAddress = ipAddress;
        log.createdAt = OffsetDateTime.now();
        return log;
    }
}
