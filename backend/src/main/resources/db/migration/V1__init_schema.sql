-- =========================================================
-- Ordo 초기 스키마 (docs/erd.md 기준)
-- =========================================================

-- 고객사 / 운영사
CREATE TABLE tenants (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('CUSTOMER', 'PROVIDER')),
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 사용자
CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL REFERENCES tenants(id),
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    name           VARCHAR(50)  NOT NULL,
    role           VARCHAR(30)  NOT NULL
                   CHECK (role IN ('REQUESTER', 'CUSTOMER_ADMIN', 'OPERATOR', 'APPROVER', 'OPS_ADMIN')),
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 운영사 사용자의 고객사 접근 범위
CREATE TABLE user_tenant_access (
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    tenant_id    BIGINT      NOT NULL REFERENCES tenants(id),
    access_role  VARCHAR(30) NOT NULL CHECK (access_role IN ('OPERATOR', 'APPROVER')),
    granted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, tenant_id)
);

-- 티켓
CREATE TABLE tickets (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_no         VARCHAR(30)  NOT NULL UNIQUE,
    tenant_id         BIGINT       NOT NULL REFERENCES tenants(id),
    requester_id      BIGINT       NOT NULL REFERENCES users(id),
    request_type      VARCHAR(30)
                      CHECK (request_type IN ('INCIDENT', 'SERVICE_REQUEST', 'CHANGE', 'INQUIRY')),
    category          VARCHAR(50),
    status            VARCHAR(30)  NOT NULL DEFAULT 'SUBMITTED',
    priority          VARCHAR(5)   CHECK (priority IN ('P1', 'P2', 'P3', 'P4')),
    risk_grade        VARCHAR(10)  CHECK (risk_grade IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    subject           VARCHAR(200) NOT NULL,
    original_request  TEXT         NOT NULL,
    detail_json       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    environment       VARCHAR(20)  CHECK (environment IN ('PRODUCTION', 'STAGING', 'DEV')),
    affected_service  VARCHAR(100),
    assigned_team     VARCHAR(50),
    assigned_user_id  BIGINT       REFERENCES users(id),
    scheduled_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_tickets_tenant_status   ON tickets (tenant_id, status);
CREATE INDEX idx_tickets_assignee_status ON tickets (assigned_user_id, status);

-- AI 트리아지 결과 (재분석마다 새 행)
CREATE TABLE ai_triage_results (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id      BIGINT       NOT NULL REFERENCES tickets(id),
    attempt_no     INT          NOT NULL,
    model_name     VARCHAR(100) NOT NULL,
    result_status  VARCHAR(20)  NOT NULL CHECK (result_status IN ('SUCCESS', 'INVALID_JSON', 'FAILED')),
    result_json    JSONB,
    confidence     NUMERIC(3, 2),
    reviewed_by    BIGINT       REFERENCES users(id),
    confirmed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (ticket_id, attempt_no)
);

-- 위험도 평가 (재평가마다 새 행, is_current로 최신 표시)
CREATE TABLE risk_assessments (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id       BIGINT      NOT NULL REFERENCES tickets(id),
    score           INT         NOT NULL,
    grade           VARCHAR(10) NOT NULL CHECK (grade IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    factors_json    JSONB       NOT NULL,
    policy_version  VARCHAR(20) NOT NULL,
    is_current      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_risk_current ON risk_assessments (ticket_id) WHERE is_current;

-- 고객사별 SLA 정책
CREATE TABLE sla_policies (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id           BIGINT     NOT NULL REFERENCES tenants(id),
    priority            VARCHAR(5) NOT NULL CHECK (priority IN ('P1', 'P2', 'P3', 'P4')),
    response_minutes    INT        NOT NULL,
    resolution_minutes  INT        NOT NULL,
    UNIQUE (tenant_id, priority)
);

-- 티켓별 SLA 추적
CREATE TABLE ticket_slas (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id             BIGINT      NOT NULL UNIQUE REFERENCES tickets(id),
    sla_policy_id         BIGINT      NOT NULL REFERENCES sla_policies(id),
    started_at            TIMESTAMPTZ NOT NULL,
    response_due_at       TIMESTAMPTZ NOT NULL,
    resolution_due_at     TIMESTAMPTZ NOT NULL,
    responded_at          TIMESTAMPTZ,
    resolved_at           TIMESTAMPTZ,
    paused_at             TIMESTAMPTZ,
    paused_total_minutes  INT         NOT NULL DEFAULT 0,
    response_breached     BOOLEAN     NOT NULL DEFAULT FALSE,
    resolution_breached   BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_sla_open_due ON ticket_slas (resolution_due_at) WHERE resolved_at IS NULL;

-- 승인
CREATE TABLE approvals (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id     BIGINT      NOT NULL REFERENCES tickets(id),
    approver_id   BIGINT      NOT NULL REFERENCES users(id),
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    comment       TEXT,
    requested_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at    TIMESTAMPTZ
);

-- 댓글
CREATE TABLE ticket_comments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id   BIGINT      NOT NULL REFERENCES tickets(id),
    author_id   BIGINT      NOT NULL REFERENCES users(id),
    body        TEXT        NOT NULL,
    visibility  VARCHAR(10) NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'INTERNAL')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 감사 로그 (INSERT 전용)
CREATE TABLE audit_logs (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id    BIGINT      REFERENCES tenants(id),
    actor_id     BIGINT      REFERENCES users(id),
    ticket_id    BIGINT      REFERENCES tickets(id),
    action       VARCHAR(50) NOT NULL,
    before_json  JSONB,
    after_json   JSONB,
    ip_address   VARCHAR(45),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_ticket_created ON audit_logs (ticket_id, created_at);

-- 감사 로그 수정·삭제 차단
CREATE FUNCTION prevent_audit_log_modification() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_append_only
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_modification();
