# Ordo

<img src="frontend/public/logo.svg" alt="Ordo" width="200" />

> Ordo(오르도) - 라틴어로 '질서' — 흩어진 IT 운영 요청에 질서를 부여하는 AI 기반 요청 관리 플랫폼

사내 IT 운영 요청을 자연어로 접수하면 AI가 유형·카테고리·누락 항목을 분석하고, 규칙 기반 정책 엔진이 위험 등급을 산정한다. SLA 추적·감사 로그·실시간 알림까지 연결하는 B2B 엔터프라이즈 IT 운영 요청 관리 플랫폼.

## 핵심 흐름

```
자연어 요청 → AI 트리아지(분류·항목 추출·누락 탐지) → 누락 정보 보완
→ 정책 엔진(위험 점수·등급) → 승인 → 작업·검증·종료
(전 과정 SLA 추적 · 감사 로그 기록)
```

- **AI**는 추천·추출만 수행
- **정책 엔진**이 규칙 기반으로 위험등급 확정
- **사람**이 승인·실행 결정

## 기술 스택

| 영역 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Security (JWT), Spring Data JPA |
| Frontend | React 18, TypeScript, Vite |
| Database | PostgreSQL 16 |
| AI | OpenAI-compatible / Gemini / 키워드 폴백 (전략 패턴으로 교체 가능) |
| 실시간 알림 | Server-Sent Events (SSE) |
| Infra | Docker Compose, AWS (ECS Fargate, ALB, RDS) |

## 구현된 기능

### 대화형 AI 어시스턴트
- 자연어 요청을 이해해 `create_request` / `get_request` / `list_my_requests` / `list_requests` 도구 자동 호출
- 역할별 허용 도구 분리 — 역할 밖 도구는 실행 차단
- 로컬 LLM(Ollama 등)이 tool_calls 대신 텍스트로 함수 호출을 뱉을 때 파싱 복구 (`ToolCallRecovery`)
- 응답 내 영어 enum 코드를 한글로 자동 후처리
- 채팅 히스토리는 클라이언트가 매 턴 재전송 (서버 무상태, 최대 20턴)

### AI 트리아지 파이프라인
- 요청 등록 즉시 LLM 분석 → 요청 유형(변경/장애/문의 등)·카테고리·환경·영향 서비스 추출
- LLM 전송 전 비밀번호·API 키·주민번호·카드번호 자동 마스킹 (`SensitiveDataMasker`)
- 누락 항목 감지 시 상태를 `INFO_REQUIRED`로 전환 + SLA 자동 정지
- 보완 정보 제출 → 트리아지 재실행 → SLA 재개 (정지 기간만큼 마감 연장)
- 장애(INCIDENT) 요청은 누락 항목이 있어도 SLA 정지 없이 즉시 처리
- 엔진 전략 패턴: `openai-compatible` / `gemini` / `keyword` 중 `llm.provider` 프로퍼티로 선택

### 위험도 정책 엔진
- 변경(CHANGE) 요청에 한해 규칙 기반 위험 점수 산정 → LOW / MEDIUM / HIGH / CRITICAL 등급
- 보완 완료 시 자동 재평가 및 이력 누적

### SLA 관리
- 요청 접수(SUBMITTED) 시점에 타이머 시작 (우선순위 확정 전 P3 기준 임시 마감)
- 누락 정보 대기 중 SLA 정지 / 보완 완료 시 재개 (정지 시간 제외)

### 실시간 알림 (SSE)
- 장애 발생(`INCIDENT_CREATED`) · SLA 만료 임박(`SLA_NEAR`) 이벤트 푸시
- 역할·테넌트 기준 서버 측 수신자 필터링
  - OPS_ADMIN / APPROVER: 전체 수신
  - OPERATOR: 담당 테넌트만
  - REQUESTER: SLA_NEAR는 자기 티켓만
- 10초 heartbeat으로 프록시 연결 유지, 클라이언트 자동 재연결

### 인증·인가 (멀티테넌트)
- Stateless JWT — userId · tenantId · role · email 포함
- 5개 역할: `REQUESTER` · `CUSTOMER_ADMIN` · `OPERATOR` · `APPROVER` · `OPS_ADMIN`
- 테넌트 격리: 서비스 레이어 전 접점에서 tenantId 검증, 위반 시 접근 거부 감사 로그 기록

### 감사 로그
- 모든 상태 전환·AI 분석·위험도 평가·접근 거부 이벤트 기록
- 보안 이벤트는 독립 트랜잭션으로 커밋 (메인 작업 롤백과 무관하게 보존)

### DRAFT → 접수 2단계 흐름
- 어시스턴트가 생성한 요청서는 `DRAFT` 상태 — SLA 미시작, 백오피스 미노출
- 사용자가 요청서 카드의 **접수하기** 버튼을 눌러야 `SUBMITTED`로 전환, SLA 타이머 시작

## 프로젝트 구조

```
ordo-itsm/
├── backend/     # Spring Boot API 서버
├── frontend/    # React 웹 클라이언트
├── docs/        # ERD, API 명세, 기획 문서, 테스트 시나리오
└── docker-compose.yml
```

## 로컬 실행

```bash
cp .env.example .env
docker compose up -d      # PostgreSQL 실행 (포트 5433)
```

## 문서

- [ERD](docs/erd.md)
- [API 명세](docs/api.md)
- [테스트 시나리오](docs/test/Ordo_테스트_시나리오.md)
