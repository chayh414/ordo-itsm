# Ordo spike — qwen2.5:7b tool-calling 결과

- 엔드포인트: http://localhost:11435/v1
- 모델: qwen2.5:7b
- 시나리오별 반복: 5 회
- 통과 기준: 각 시나리오 성공률 >= 70% + round-trip PASS
- 실행 시각: 2026-10-10 17:39

| # | 세트 | 요청 | 기대 도구 | 성공 | 성공률 | 평균(ms) |
|---:|---|---|---|---:|---:|---:|
| 1 | REQUESTER | 운영 DB 학생 포털 테이블에 컬럼 하나 추가하는 요청 등록해줘. 23시 작업, 테스트는 끝났고 롤백 절차는 아직 못 짰어. | create_request | 0/5 | 0 | 4053 |
| 2 | REQUESTER | 내가 올린 요청들 상태 좀 보여줘. | list_my_requests | 0/5 | 0 | 4037 |
| 3 | REQUESTER | 보완 대기 중인 내 요청만 추려줘. | list_my_requests | 0/5 | 0 | 4054 |
| 4 | REQUESTER | ORD-20261008-0003 요청 어떻게 처리되고 있어? | get_request | 0/5 | 0 | 4049 |
| 5 | REQUESTER | 안녕! 오늘 날씨 어때? | (no tool) | 0/5 | 0 | 4048 |
| 6 | REQUESTER | 방화벽 포트 열려면 보통 어떻게 해야 해? | (no tool) | 0/5 | 0 | 4051 |
| 7 | OPERATOR | A대학교 요청 중에 보완 대기 중인 거 보여줘. | list_requests | 0/5 | 0 | 4041 |

**Round-trip (tool result → 자연어 답변):** FAIL

**Overall:** success_rate=0 · gate=FAIL

원시 로그: `docs/scripts/spike-tools.log.jsonl`
