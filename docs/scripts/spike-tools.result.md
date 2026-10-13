# Ordo spike — qwen2.5:7b tool-calling 결과

- 엔드포인트: http://localhost:11435/v1
- 모델: qwen2.5:7b
- 시나리오별 반복: 5 회
- 통과 기준: 각 시나리오 성공률 >= 70% + round-trip PASS
- 실행 시각: 2026-10-13 16:38

| # | 세트 | 요청 | 기대 도구 | 성공 | 성공률 | 평균(ms) |
|---:|---|---|---|---:|---:|---:|
| 1 | REQUESTER | 운영 DB 학생 포털 테이블에 컬럼 하나 추가하는 요청 등록해줘. 23시 작업, 테스트는 끝났고 롤백 절차는 아직 못 짰어. | create_request | 5/5 | 1 | 1850 |
| 2 | REQUESTER | 내가 올린 요청들 상태 좀 보여줘. | list_my_requests | 5/5 | 1 | 621 |
| 3 | REQUESTER | 보완 대기 중인 내 요청만 추려줘. | list_my_requests | 5/5 | 1 | 724 |
| 4 | REQUESTER | ORD-20261008-0003 요청 어떻게 처리되고 있어? | get_request | 5/5 | 1 | 1000 |
| 5 | REQUESTER | 안녕! 오늘 날씨 어때? | (no tool) | 5/5 | 1 | 970 |
| 6 | REQUESTER | 방화벽 포트 열려면 보통 어떻게 해야 해? | (no tool) | 5/5 | 1 | 5778 |
| 7 | OPERATOR | A대학교 요청 중에 보완 대기 중인 거 보여줘. | list_requests | 5/5 | 1 | 737 |
| 8 | REQUESTER | 오늘 23시에 운영 DB 학생 포털 테이블에 컬럼 추가하려고요. 테스트는 했는데 롤백 절차는 아직 없어요. | create_request | 5/5 | 1 | 1641 |

**Round-trip (tool result → 자연어 답변):** PASS

> 티켓 ORD-20261008-0003의 상태는 정보 요청(INFO_REQUIRED)입니다. 현재까지는 롤백 계획이 누락되어 있어요. 이 문제는 2026년 10월 10일 오전 9시까지 해결해야 합니다. 생산 환경과 데이터베이스 스키마 변경, 그리고 롤백 계획 누락이 위험 요소로 지목되어 있어요.

**Overall:** success_rate=1 · gate=PASS

원시 로그: `docs/scripts/spike-tools.log.jsonl`
