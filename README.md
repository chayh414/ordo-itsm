# Ordo

> 라틴어로 '질서' — 흩어진 IT 운영 요청에 질서를 부여하는 AI 기반 요청 관리 플랫폼

고객사의 비정형 IT 운영 요청을 AI가 구조화하고, 누락 정보 보완·변경 위험도·승인·SLA·감사 로그까지 연결하는 B2B 엔터프라이즈 IT 운영 요청 관리 플랫폼

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
| Backend | Java, Spring Boot, Spring Security(JWT), Spring Data JPA |
| Frontend | React, TypeScript, Vite |
| Database | PostgreSQL |
| AI | 외부 LLM API (Structured Output) |
| Infra | Docker, AWS (ECS Fargate, ALB, RDS) |

## 프로젝트 구조

```
ordo-itsm/
├── backend/     # Spring Boot API 서버
├── frontend/    # React 웹 클라이언트
├── docs/        # ERD, API 명세, 기획 문서
└── docker-compose.yml
```

## 로컬 실행

```bash
cp .env.example .env
docker compose up -d      # PostgreSQL 실행
```

## 문서

- [ERD](docs/erd.md)

## 개발 로드맵

- [ ] 1주차: 분석·설계 (ERD, API 명세, 상태 전이, AI JSON 스키마)
- [ ] 2주차: 로그인·JWT·테넌트/역할 권한·기본 CRUD
- [ ] 3주차: 티켓 등록·상세, LLM 연동, 누락 정보 보완
- [ ] 4주차: 정책 엔진, 승인 워크플로우, SLA
- [ ] 5주차: 감사 로그, 대시보드, 배포
- [ ] 6주차: 테스트, 시연 준비
