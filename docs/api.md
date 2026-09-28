# Ordo API 명세 v1

## 0. 공통 규칙

| 항목 | 규칙 |
|---|---|
| Base URL | `/api` |
| 인증 | 로그인 외 모든 요청에 `Authorization: Bearer {accessToken}` |
| 형식 | 요청·응답 모두 `application/json`, 시간은 ISO-8601 (`2026-10-01T23:00:00+09:00`) |
| 테넌트 | 클라이언트가 `tenantId`를 보내지 않는다. **서버가 토큰에서 꺼내 강제 적용**한다 |
| 목록 | `?page=0&size=20&sort=createdAt,desc` |

### 에러 응답

```json
{
  "code": "TICKET_INVALID_TRANSITION",
  "message": "PENDING_APPROVAL 상태에서는 IN_PROGRESS로 변경할 수 없습니다.",
  "timestamp": "2026-10-01T14:03:11+09:00"
}
```

| HTTP | 의미 | 대표 code |
|---|---|---|
| 400 | 입력값 오류 | `VALIDATION_FAILED` |
| 401 | 토큰 없음·만료 | `AUTH_UNAUTHORIZED` |
| 403 | 권한·테넌트 불일치, 자기 승인 | `ACCESS_DENIED`, `SELF_APPROVAL_FORBIDDEN` |
| 404 | 대상 없음 | `TICKET_NOT_FOUND` |
| 409 | 허용되지 않은 상태 전이 | `TICKET_INVALID_TRANSITION` |
| 502 | LLM 호출 실패 | `AI_TRIAGE_FAILED` |

> 다른 고객사 티켓 조회는 존재 여부를 숨기기 위해 403 대신 404를 반환하는 방식도 고려. MVP는 403 + 감사 로그 기록.

### 역할 약어
`REQ` 고객 요청자 · `CADM` 고객사 관리자 · `OPR` 운영 담당자 · `APV` 승인자 · `ADM` 운영 관리자

---

## 1. 인증

### POST /api/auth/login
권한: 전체

```json
// Request
{ "email": "requester@a-univ.ac.kr", "password": "Password123!" }

// Response 200
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

### GET /api/auth/me
권한: 로그인 사용자

```json
// Response 200
{
  "id": 3,
  "email": "requester@a-univ.ac.kr",
  "name": "김요청",
  "role": "REQUESTER",
  "tenant": { "id": 2, "code": "A-UNIV", "name": "A대학교" }
}
```

---

## 2. 티켓

### POST /api/tickets — 요청 등록
권한: REQ, CADM
동작: 원문 저장 → 티켓번호 발급 → SLA 타이머 시작(P3 임시) → 감사 로그 → **AI 트리아지 자동 실행**

```json
// Request
{
  "subject": "학생 포털 DB 컬럼 추가",
  "originalRequest": "오늘 23시에 운영 DB 학생 포털 테이블에 컬럼을 추가하려고 합니다. 테스트는 했지만 롤백 절차는 아직 정리하지 못했습니다."
}

// Response 201
{
  "id": 101,
  "ticketNo": "ORD-20261001-0001",
  "status": "SUBMITTED",
  "createdAt": "2026-10-01T14:00:00+09:00"
}
```

### GET /api/tickets — 목록
권한: 전체 (역할별 조회 범위 자동 적용)
필터: `status`, `requestType`, `priority`, `riskGrade`, `assignedToMe=true`, `slaStatus=AT_RISK|BREACHED`

```json
// Response 200
{
  "content": [
    {
      "id": 101,
      "ticketNo": "ORD-20261001-0001",
      "tenantName": "A대학교",
      "subject": "학생 포털 DB 컬럼 추가",
      "requestType": "CHANGE",
      "status": "INFO_REQUIRED",
      "priority": "P3",
      "riskGrade": "CRITICAL",
      "assignee": null,
      "sla": { "resolutionDueAt": "2026-10-04T14:00:00+09:00", "paused": true, "breached": false },
      "createdAt": "2026-10-01T14:00:00+09:00"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

### GET /api/tickets/{ticketId} — 상세
권한: 조회 범위 내 사용자

```json
// Response 200
{
  "id": 101,
  "ticketNo": "ORD-20261001-0001",
  "tenant": { "id": 2, "name": "A대학교" },
  "requester": { "id": 3, "name": "김요청" },
  "subject": "학생 포털 DB 컬럼 추가",
  "originalRequest": "오늘 23시에 운영 DB ...",
  "requestType": "CHANGE",
  "category": "DATABASE_SCHEMA",
  "status": "INFO_REQUIRED",
  "priority": "P3",
  "environment": "PRODUCTION",
  "affectedService": "학생 포털",
  "scheduledAt": "2026-10-01T23:00:00+09:00",
  "details": { "testPlan": "스테이징 검증 완료", "rollbackPlan": null },
  "assignee": null,
  "triage": { "attemptNo": 1, "confidence": 0.88, "missingFields": ["rollbackPlan"], "confirmed": false },
  "risk": { "score": 9, "grade": "CRITICAL", "policyVersion": "v1" },
  "sla": {
    "responseDueAt": "2026-10-01T18:00:00+09:00",
    "resolutionDueAt": "2026-10-04T14:00:00+09:00",
    "paused": true,
    "pausedTotalMinutes": 12,
    "responseBreached": false,
    "resolutionBreached": false
  },
  "allowedTransitions": ["CANCELLED"],
  "createdAt": "2026-10-01T14:00:00+09:00",
  "updatedAt": "2026-10-01T14:12:00+09:00"
}
```

> `allowedTransitions`: 현재 사용자가 지금 누를 수 있는 상태 변경 목록. 프론트는 이 값으로 버튼을 그린다(판정은 서버가 함).

### POST /api/tickets/{ticketId}/supplement — 누락 정보 보완
권한: REQ(본인 티켓), CADM
동작: 상세 항목 병합 → SLA 재개 → AI 재분석 → 위험도 재계산

```json
// Request
{ "details": { "rollbackPlan": "컬럼 DROP 스크립트 준비, 작업 전 테이블 백업" } }

// Response 200
{
  "status": "RISK_REVIEWED",
  "missingFields": [],
  "risk": { "score": 7, "grade": "HIGH", "previous": { "score": 9, "grade": "CRITICAL" } }
}
```

### PATCH /api/tickets/{ticketId} — 기본 정보 수정
권한: OPR, ADM
수정 가능: `priority`, `requestType`, `category`, `environment`, `affectedService`, `scheduledAt`
동작: 우선순위 변경 시 SLA 마감 재계산, 변경 전·후 감사 로그

```json
{ "priority": "P2" }
```

### POST /api/tickets/{ticketId}/assign — 담당자 배정
권한: OPR, ADM

```json
{ "assigneeId": 7, "team": "DB_OPERATION" }
```

### POST /api/tickets/{ticketId}/status — 상태 변경
권한: 상태별 상이 (상태 전이표 참고)

```json
// Request
{ "to": "COMPLETED", "note": "컬럼 추가 완료, 애플리케이션 정상 동작 확인" }

// Response 409
{ "code": "TICKET_INVALID_TRANSITION", "message": "승인되지 않은 HIGH 변경은 작업을 시작할 수 없습니다." }
```

### POST /api/tickets/{ticketId}/comments — 댓글
권한: 조회 범위 내 사용자 (`INTERNAL`은 운영사만 작성·조회)

```json
{ "body": "작업 전 백업 경로 공유 부탁드립니다.", "visibility": "PUBLIC" }
```

### GET /api/tickets/{ticketId}/comments
권한: 조회 범위 내 사용자 (고객사 사용자는 `PUBLIC`만)

---

## 3. AI 트리아지

### POST /api/tickets/{ticketId}/triage — 재분석
권한: OPR, ADM (등록·보완 시에는 자동 실행)

```json
// Response 200
{
  "attemptNo": 2,
  "resultStatus": "SUCCESS",
  "modelName": "llm-model-name",
  "confidence": 0.91,
  "result": {
    "requestType": "CHANGE",
    "category": "DATABASE_SCHEMA",
    "environment": "PRODUCTION",
    "requestedTime": "23:00",
    "recommendedPriority": "P3",
    "recommendedTeam": "DB_OPERATION",
    "affectedService": "학생 포털",
    "missingFields": [],
    "riskFactors": ["production_environment", "database_schema_change", "night_work"],
    "evidence": { "night_work": "오늘 23시에" }
  }
}
```

### GET /api/tickets/{ticketId}/triage
최신 분석 결과. `?history=true`면 전체 회차 반환.

### POST /api/tickets/{ticketId}/triage/confirm — 검토 확정
권한: OPR, ADM
동작: 사람이 수정한 값 반영(수정값 우선) → 우선순위 확정 → SLA 재계산 → 상태 `TRIAGED`

```json
{
  "requestType": "CHANGE",
  "category": "DATABASE_SCHEMA",
  "priority": "P3",
  "team": "DB_OPERATION"
}
```

---

## 4. 위험도·승인

### GET /api/tickets/{ticketId}/risk-assessments
권한: 조회 범위 내 사용자

```json
// Response 200 (최신순)
[
  {
    "score": 7, "grade": "HIGH", "current": true, "policyVersion": "v1",
    "factors": [
      { "code": "production_environment", "points": 3 },
      { "code": "database_schema_change", "points": 3 },
      { "code": "night_work", "points": 1 }
    ],
    "createdAt": "2026-10-01T14:20:00+09:00"
  },
  { "score": 9, "grade": "CRITICAL", "current": false, "createdAt": "2026-10-01T14:01:00+09:00" }
]
```

### POST /api/tickets/{ticketId}/approvals — 승인 요청
권한: OPR, ADM
조건: 위험등급 HIGH·CRITICAL, 누락 정보 없음, 승인자 ≠ 요청자·담당자

```json
// Request
{ "approverId": 9 }

// Response 201
{ "approvalId": 55, "status": "PENDING" }
```

### GET /api/approvals?status=PENDING — 내 승인 대기 목록
권한: APV

### POST /api/approvals/{approvalId}/approve
권한: APV (지정된 승인자 본인)

```json
{ "comment": "롤백 계획 확인. 23시 작업 승인" }
```

### POST /api/approvals/{approvalId}/reject
권한: APV, **comment 필수**

```json
{ "comment": "영향 범위 확인 필요. 수강신청 기간과 겹치는지 확인 바랍니다." }
```

---

## 5. SLA·감사·대시보드

### GET /api/tickets/{ticketId}/sla
상세 응답의 `sla` 객체와 동일.

### GET /api/tickets/{ticketId}/audit-logs
권한: OPR, ADM, CADM

```json
[
  {
    "action": "RISK_REASSESSED",
    "actor": { "id": 3, "name": "김요청" },
    "before": { "score": 9, "grade": "CRITICAL" },
    "after":  { "score": 7, "grade": "HIGH" },
    "createdAt": "2026-10-01T14:20:00+09:00"
  }
]
```

### GET /api/dashboard/overview
권한: ADM (CADM은 자기 고객사 기준)

```json
{
  "openTickets": 24,
  "byStatus": { "INFO_REQUIRED": 3, "PENDING_APPROVAL": 2, "IN_PROGRESS": 8 },
  "byRiskGrade": { "LOW": 10, "MEDIUM": 7, "HIGH": 5, "CRITICAL": 2 },
  "slaAtRisk": 4,
  "slaBreached": 1
}
```

---

## 6. 감사 로그 action 목록

`TICKET_CREATED` · `AI_TRIAGED` · `AI_TRIAGE_FAILED` · `TRIAGE_CONFIRMED` · `TICKET_UPDATED` · `INFO_SUPPLEMENTED` · `RISK_ASSESSED` · `RISK_REASSESSED` · `TICKET_ASSIGNED` · `STATUS_CHANGED` · `SLA_PAUSED` · `SLA_RESUMED` · `APPROVAL_REQUESTED` · `APPROVED` · `REJECTED` · `COMMENT_ADDED` · `LOGIN_FAILED` · `ACCESS_DENIED`
