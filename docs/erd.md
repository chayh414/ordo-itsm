# Ordo ERD v1

> DB: PostgreSQL / 모든 고객사 데이터 테이블은 `tenant_id` 기준 논리 격리

## 1. 전체 관계도

```mermaid
erDiagram
    tenants ||--o{ users : "소속"
    tenants ||--o{ user_tenant_access : "접근 허용"
    users ||--o{ user_tenant_access : "운영사 사용자"
    tenants ||--o{ sla_policies : "고객사별 SLA"
    tenants ||--o{ tickets : "소유"
    users ||--o{ tickets : "요청"
    tickets ||--o{ ai_triage_results : "AI 분석 이력"
    tickets ||--o{ risk_assessments : "위험도 이력"
    tickets ||--|| ticket_slas : "SLA 추적"
    sla_policies ||--o{ ticket_slas : "적용"
    tickets ||--o{ approvals : "승인"
    users ||--o{ approvals : "승인자"
    tickets ||--o{ ticket_comments : "댓글"
    users ||--o{ ticket_comments : "작성"
    tickets ||--o{ audit_logs : "변경 이력"
    users ||--o{ audit_logs : "행위자"

    tenants {
        bigint id PK
        varchar code UK
        varchar name
        varchar type "CUSTOMER / PROVIDER"
        varchar status "ACTIVE / INACTIVE"
        timestamptz created_at
    }

    users {
        bigint id PK
        bigint tenant_id FK
        varchar email UK
        varchar password_hash
        varchar name
        varchar role "REQUESTER / CUSTOMER_ADMIN / OPERATOR / APPROVER / OPS_ADMIN"
        varchar status
        timestamptz created_at
    }

    user_tenant_access {
        bigint user_id PK
        bigint tenant_id PK
        varchar access_role "OPERATOR / APPROVER"
        timestamptz granted_at
    }

    tickets {
        bigint id PK
        varchar ticket_no UK "ORD-20261001-0001"
        bigint tenant_id FK
        bigint requester_id FK
        varchar request_type "INCIDENT / SERVICE_REQUEST / CHANGE / INQUIRY"
        varchar category "DATABASE_SCHEMA / FIREWALL / ACCOUNT ..."
        varchar status
        varchar priority "P1~P4"
        varchar risk_grade "LOW / MEDIUM / HIGH / CRITICAL"
        varchar subject
        text original_request
        jsonb detail_json "유형별 추출 항목(IP, 포트, 롤백계획 등)"
        varchar environment "PRODUCTION / STAGING / DEV"
        varchar affected_service
        varchar assigned_team
        bigint assigned_user_id FK
        timestamptz scheduled_at "작업 희망 시각"
        timestamptz created_at
        timestamptz updated_at
    }

    ai_triage_results {
        bigint id PK
        bigint ticket_id FK
        int attempt_no "재분석 회차"
        varchar model_name
        varchar result_status "SUCCESS / INVALID_JSON / FAILED"
        jsonb result_json
        numeric confidence
        bigint reviewed_by FK
        timestamptz confirmed_at
        timestamptz created_at
    }

    risk_assessments {
        bigint id PK
        bigint ticket_id FK
        int score
        varchar grade
        jsonb factors_json "요인별 점수"
        varchar policy_version
        boolean is_current "최신 평가 여부"
        timestamptz created_at
    }

    sla_policies {
        bigint id PK
        bigint tenant_id FK
        varchar priority "P1~P4"
        int response_minutes
        int resolution_minutes
    }

    ticket_slas {
        bigint id PK
        bigint ticket_id FK
        bigint sla_policy_id FK
        timestamptz started_at "SUBMITTED 시점"
        timestamptz response_due_at
        timestamptz resolution_due_at
        timestamptz responded_at
        timestamptz resolved_at
        timestamptz paused_at "INFO_REQUIRED 진입 시각"
        int paused_total_minutes "누적 정지 시간"
        boolean response_breached
        boolean resolution_breached
    }

    approvals {
        bigint id PK
        bigint ticket_id FK
        bigint approver_id FK
        varchar status "PENDING / APPROVED / REJECTED"
        text comment
        timestamptz requested_at
        timestamptz decided_at
    }

    ticket_comments {
        bigint id PK
        bigint ticket_id FK
        bigint author_id FK
        text body
        varchar visibility "PUBLIC / INTERNAL"
        timestamptz created_at
    }

    audit_logs {
        bigint id PK
        bigint tenant_id FK
        bigint actor_id FK
        bigint ticket_id FK "nullable (로그인 실패 등)"
        varchar action "TICKET_CREATED / AI_TRIAGED / STATUS_CHANGED ..."
        jsonb before_json
        jsonb after_json
        varchar ip_address
        timestamptz created_at
    }
```

## 2. 설계 결정 사항

| 항목 | 결정 | 이유 |
|---|---|---|
| 운영사(시스원) 표현 | `tenants.type = PROVIDER`인 테넌트로 등록 | 운영사 사용자도 `users.tenant_id`를 가지게 해서 구조를 단순화 |
| 운영사 사용자 접근 범위 | `user_tenant_access`에 등록된 고객사만 조회 | 담당하지 않는 고객사 데이터 노출 방지 |
| 유형별 추출 항목 | `tickets.detail_json`(jsonb) | 방화벽·DB·계정 요청마다 필수 항목이 달라 컬럼 고정이 비효율적 |
| AI 분석 결과 | 재분석할 때마다 새 행 추가(`attempt_no`) | 보완 전/후 AI 판단 변화를 추적 |
| 위험도 | 재계산 시 새 행 추가, `is_current`로 최신 표시 | 보완 전 9점(Critical) → 보완 후 7점(High) 이력 보존 |
| 우선순위 vs 위험등급 | `priority`(P1~P4)는 SLA용, `risk_grade`는 승인용으로 분리 | 두 개념이 섞이지 않도록 |
| SLA 시작 시점 | 티켓 `SUBMITTED` 시점 | 고객 입장의 응답 시간 기준 |
| SLA 일시정지 | `INFO_REQUIRED` 동안 정지, 누적 시간만큼 마감 연장 | 고객 보완 대기 시간이 운영사 SLA 위반으로 잡히지 않도록 |
| 감사 로그 | INSERT만 허용(UPDATE/DELETE 금지) | 위·변조 불가능한 증빙 |
| 자기 승인 방지 | `approvals.approver_id ≠ tickets.requester_id / assigned_user_id` 서버 검증 | 감사 통제 |

## 3. 우선순위 결정 규칙 (초안)

AI는 `recommendedPriority`만 추천하고, 최종 우선순위는 운영 담당자가 트리아지 확인 단계에서 확정한다.

| 우선순위 | 기준 | 첫 응답 | 해결 |
|---|---|---:|---:|
| P1 | 전체 서비스 중단 | 15분 | 4시간 |
| P2 | 핵심 기능 장애, 다수 사용자 영향 | 30분 | 8시간 |
| P3 | 일반 장애·서비스 요청·변경 | 4시간 | 3영업일 |
| P4 | 일반 문의 | 1영업일 | 5영업일 |

## 4. 인덱스 (초안)

- `tickets (tenant_id, status)` — 고객사별 목록 조회
- `tickets (assigned_user_id, status)` — 담당자별 목록
- `ticket_slas (resolution_due_at) WHERE resolved_at IS NULL` — SLA 임박 조회
- `audit_logs (ticket_id, created_at)` — 티켓별 이력 조회
- `risk_assessments (ticket_id) WHERE is_current = true`
