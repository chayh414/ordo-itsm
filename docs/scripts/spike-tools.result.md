# Ordo spike — qwen2.5:7b tool-calling 결과

- 엔드포인트: http://localhost:11435/v1
- 모델: qwen2.5:7b
- 시나리오별 반복: 5 회
- 통과 기준: 각 시나리오 성공률 >= 70% + round-trip PASS
- 실행 시각: 2026-10-13 09:57

| # | 세트 | 요청 | 기대 도구 | 성공 | 성공률 | 평균(ms) |
|---:|---|---|---|---:|---:|---:|
| 1 | REQUESTER | 운영 DB 학생 포털 테이블에 컬럼 하나 추가하는 요청 등록해줘. 23시 작업, 테스트는 끝났고 롤백 절차는 아직 못 짰어. | create_request | 5/5 | 1 | 4880 |
| 2 | REQUESTER | 내가 올린 요청들 상태 좀 보여줘. | list_my_requests | 5/5 | 1 | 1093 |
| 3 | REQUESTER | 보완 대기 중인 내 요청만 추려줘. | list_my_requests | 5/5 | 1 | 1366 |
| 4 | REQUESTER | ORD-20261008-0003 요청 어떻게 처리되고 있어? | get_request | 5/5 | 1 | 1972 |
| 5 | REQUESTER | 안녕! 오늘 날씨 어때? | (no tool) | 5/5 | 1 | 1832 |
| 6 | REQUESTER | 방화벽 포트 열려면 보통 어떻게 해야 해? | (no tool) | 5/5 | 1 | 7717 |
| 7 | OPERATOR | A대학교 요청 중에 보완 대기 중인 거 보여줘. | list_requests | 5/5 | 1 | 1306 |

**Round-trip (tool result → 자연어 답변):** PASS

> 티켓 ORD-20261008-0003은 현재 "INFO_REQUIRED" 상태입니다. 부족한 정보로 인해 추가 정보가 필요합니다. 이 요청은 데이터베이스 스키마 변경에 관한 것으로, 프로덕션 환경에서의 변경과 롤백 계획의 부재가 위험 요인으로 지정되었습니다. SLA 해상도는 2026년 10월 10일 오전 9시까지입니다.

**Overall:** success_rate=1 · gate=PASS

원시 로그: `docs/scripts/spike-tools.log.jsonl`
