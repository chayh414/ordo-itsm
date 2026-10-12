/*
 * 한국어 라벨 매핑 — 백엔드 enum·키를 UI 표시용 한글로 변환.
 * 매핑에 없는 값은 그대로 돌려줘서, 백엔드가 새 enum 추가해도 깨지지 않도록.
 */

const REQUEST_TYPE: Record<string, string> = {
  INCIDENT: '장애',
  SERVICE_REQUEST: '일반 요청',
  CHANGE: '변경 요청',
  INQUIRY: '문의',
}

const CATEGORY: Record<string, string> = {
  DATABASE_SCHEMA: 'DB 스키마',
  DATABASE_PERMISSION: 'DB 권한',
  DATABASE_PATCH: 'DB 패치',
  FIREWALL: '방화벽',
  NETWORK: '네트워크',
  SERVER: '서버',
  ACCOUNT: '계정',
  APPLICATION: '애플리케이션',
  SERVICE_OUTAGE: '서비스 장애',
  PERFORMANCE: '성능',
  GENERAL_INQUIRY: '일반 문의',
  OTHER: '기타',
}

const ENVIRONMENT: Record<string, string> = {
  PRODUCTION: '운영',
  STAGING: '스테이징',
  DEV: '개발',
}

const STATUS: Record<string, string> = {
  DRAFT: '요청서 초안 · 접수 전',
  SUBMITTED: '접수됨',
  TRIAGED: '분류 완료',
  INFO_REQUIRED: '보완 대기',
  RISK_REVIEWED: '위험도 검토 완료',
  PENDING_APPROVAL: '승인 대기',
  APPROVED: '승인됨',
  REJECTED: '반려됨',
  ASSIGNED: '담당자 배정',
  SCHEDULED: '작업 예정',
  IN_PROGRESS: '처리 중',
  RESOLVED: '해결됨',
  COMPLETED: '완료',
  VERIFIED: '검증 완료',
  CLOSED: '종료',
  CANCELLED: '취소됨',
}

/** 요청서 상단 메타용 — "요청서 초안 · 접수 전", "접수됨 · 검토 중" 식. */
const DOC_HEADER: Record<string, string> = {
  DRAFT: '요청서 초안 · 접수 전',
  SUBMITTED: '접수됨 · 검토 중',
  INFO_REQUIRED: '보완 대기',
}

const RISK_FACTOR: Record<string, string> = {
  production_environment: '운영 환경',
  database_schema_change: 'DB 스키마 변경',
  database_permission_change: 'DB 권한 변경',
  firewall_change: '방화벽 변경',
  network_change: '네트워크 변경',
  rollback_plan_missing: '롤백 계획 없음',
  test_plan_missing: '테스트 계획 없음',
  night_work: '심야 작업',
}

const RISK_GRADE: Record<string, string> = {
  LOW: '낮음',
  MEDIUM: '보통',
  HIGH: '높음',
  CRITICAL: '매우 높음',
}

const FIELD_LABEL: Record<string, string> = {
  affectedService: '대상 서비스',
  occurredAt: '발생 시각',
  impactScope: '영향 범위',
  errorMessage: '오류 메시지',
  sourceIp: '출발지 IP',
  destinationIp: '목적지 IP',
  port: '포트',
  protocol: '프로토콜',
  scheduledTime: '작업 희망 일시',
  reason: '요청 사유',
  targetDatabase: '대상 DB',
  changeDescription: '변경 내용',
  environment: '대상 환경',
  testPlan: '테스트 계획',
  rollbackPlan: '롤백 절차',
  targetSystem: '대상 시스템',
  targetAccount: '대상 계정',
  requiredPermission: '필요 권한',
  usagePeriod: '사용 기간',
}

export const label = {
  requestType: (v?: string | null) => (v ? REQUEST_TYPE[v] ?? v : '—'),
  category: (v?: string | null) => (v ? CATEGORY[v] ?? v : '—'),
  environment: (v?: string | null) => (v ? ENVIRONMENT[v] ?? v : '—'),
  status: (v?: string | null) => (v ? STATUS[v] ?? v : '—'),
  docHeader: (v?: string | null) => (v ? DOC_HEADER[v] ?? STATUS[v] ?? v : '—'),
  riskFactor: (v: string) => RISK_FACTOR[v] ?? v,
  riskGrade: (v?: string | null) => (v ? RISK_GRADE[v] ?? v : '—'),
  field: (v: string) => FIELD_LABEL[v] ?? v,
}

/** OffsetDateTime (ISO) → "오늘 23:00" 류 간결 포맷. 날짜가 오늘이 아니면 "M/D HH:mm". */
export function formatWhen(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  const hhmm = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  if (sameDay) return `오늘 ${hhmm}`
  return `${d.getMonth() + 1}/${d.getDate()} ${hhmm}`
}

/** HH:mm 문자열 (triage result 의 requestedTime) → "오늘 23:00" (단순 변환). */
export function formatRequestedTime(hhmm: string | null | undefined): string {
  if (!hhmm) return '—'
  return `오늘 ${hhmm}`
}
