# Ordo spike — qwen2.5:7b tool-calling 결과

- 엔드포인트: http://localhost:11435/v1
- 모델: qwen2.5:7b
- 시나리오별 반복: 5 회
- 통과 기준: 각 시나리오 성공률 >= 70% + round-trip PASS
- 실행 시각: 2026-10-13 14:33

| # | 세트 | 요청 | 기대 도구 | 성공 | 성공률 | 평균(ms) |
|---:|---|---|---|---:|---:|---:|
| 1 | REQUESTER | 운영 DB 학생 포털 테이블에 컬럼 하나 추가하는 요청 등록해줘. 23시 작업, 테스트는 끝났고 롤백 절차는 아직 못 짰어. | create_request | 5/5 | 1 | 3870 |
| 6 | REQUESTER | 방화벽 포트 열려면 보통 어떻게 해야 해? | (no tool) | 5/5 | 1 | 9529 |
| 8 | REQUESTER | 오늘 23시에 운영 DB 학생 포털 테이블에 컬럼 추가하려고요. 테스트는 했는데 롤백 절차는 아직 없어요. | create_request | 5/5 | 1 | 3724 |

**Round-trip (tool result → 자연어 답변):** PASS

> 티켓 ORD-20261008-0003의 상태는 정보 요청(INFO_REQUIRED)입니다. 현재까지의 처리 상황은 다음과 같습니다:

- 카테고리: 데이터베이스 스키마 변경
- 부족한 정보: 롤백 계획(rollbackPlan)
- SLA 해지 예정 시간: 2026-10-10 09:00 (UTC+9)
- 위험 요인: 프로덕션 환경, 데이터베이스 스키마 변경, 롤백 계획 부족

관리자분들이 롤백 계획을 제공해 주시면 처리가 진행될 수 있습니다.

**Overall:** success_rate=1 · gate=PASS

원시 로그: `docs/scripts/spike-tools.log.jsonl`
