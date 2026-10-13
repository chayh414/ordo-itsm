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

/**
 * 백엔드 TriageValidator 가 만든 보정 로그 ("[FIX] riskFactor+... (reason)") 를
 * 화면 노출용 사람 말로 변환. 접두사([FIX]/[FILL]/[NORM]/[SUP]) 와 영어 키를 숨긴다.
 * 매칭 실패 시 원문 그대로 돌려주어 안전 장치.
 */
export function formatCorrection(raw: string): string {
  if (!raw) return ''

  // 1) requestType 보정
  let m = raw.match(/^\[FIX\]\s*requestType=([A-Z_]+)→([A-Z_]+)/)
  if (m) {
    return `AI 가 '${label.requestType(m[1])}' 로 봤는데 '${label.requestType(m[2])}' 로 바로잡았어요`
  }

  // 2) 위험 요인 추가/제거
  m = raw.match(/^\[(FIX|FILL)\]\s*riskFactor\+([a-z_]+)/)
  if (m) {
    return `AI 가 놓친 '${label.riskFactor(m[2])}' 위험 요인을 규칙으로 추가했어요`
  }
  m = raw.match(/^\[FIX\]\s*riskFactor-([a-z_]+)/)
  if (m) {
    return `원문 근거가 확실해서 '${label.riskFactor(m[1])}' 위험 요인을 제거했어요`
  }

  // 3) 누락 필드
  m = raw.match(/^\[(FIX|FILL)\]\s*missingField\+([a-zA-Z]+)/)
  if (m) {
    return `'${label.field(m[2])}' 가 비어 있어 확인이 필요합니다`
  }
  m = raw.match(/^\[FIX\]\s*missingField-([a-zA-Z]+)/)
  if (m) {
    return `원문에 '${label.field(m[1])}' 근거가 있어 보완 요청에서 뺐어요`
  }

  // 4) 작업 예정 시각
  m = raw.match(/^\[FIX\]\s*requestedTime=?:?\s*([^→]+)→null/)
  if (m) {
    return '원문에 작업 시각 근거가 없어 작업 예정 시간을 비웠어요'
  }
  m = raw.match(/^\[FIX\]\s*requestedTime[:=]\s*(\d{2}:\d{2})→(\d{2}:\d{2})/)
  if (m) {
    return `작업 예정 시간을 원문 기준으로 ${m[1]} → ${m[2]} 로 바로잡았어요`
  }

  // 5) 확신도 하향
  if (/^\[FIX\]\s*confidence/.test(raw)) {
    return '보정이 있어 담당자가 한 번 더 확인합니다'
  }

  // 6) [NORM] factors cleared
  if (/^\[NORM\]\s*riskFactors cleared/.test(raw)) {
    return '장애·문의 유형이라 변경 전용 위험 요인은 비웠어요'
  }

  // 7) [SUP] 고객 보완
  m = raw.match(/^\[SUP\]\s*([a-zA-Z]+)\s*고객 보완/)
  if (m) {
    return `'${label.field(m[1])}' 를 고객이 보완했어요`
  }

  // 매칭 실패 — 접두사만 떼고 돌려줌
  return raw.replace(/^\[(FIX|FILL|NORM|SUP)\]\s*/, '')
}
