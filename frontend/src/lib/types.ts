/*
 * 백엔드 응답 타입 정의. 백엔드의 record/DTO 와 1:1 매핑.
 * (일부 필드는 nullable — 트리아지 실패 시 null)
 */

export interface SlaResponse {
  basis: string
  responseDueAt: string
  resolutionDueAt: string
  paused: boolean
  pausedTotalMinutes: number
  responseBreached: boolean
  resolutionBreached: boolean
}

export interface TicketCreatedResponse {
  id: number
  ticketNo: string
  status: string
  sla: SlaResponse | null
  triage: TriageResponse | null
  createdAt: string
}

export interface TicketSummaryResponse {
  id: number
  ticketNo: string
  tenantName: string
  requesterName: string
  subject: string
  requestType: string | null
  status: string
  priority: string | null
  riskGrade: string | null
  sla: SlaResponse | null
  createdAt: string
}

export interface TicketDetailResponse {
  id: number
  ticketNo: string
  tenant: { id: number; name: string }
  requester: { id: number; name: string }
  subject: string
  originalRequest: string
  requestType: string | null
  category: string | null
  status: string
  priority: string | null
  riskGrade: string | null
  environment: string | null
  affectedService: string | null
  scheduledAt: string | null
  details: Record<string, unknown>
  sla: SlaResponse | null
  /** 장애(INCIDENT) 요청의 1차 통보 기한. 그 외 요청은 null. */
  firstResponseDueAt: string | null
  createdAt: string
  updatedAt: string
}

export interface RiskHistoryEntry {
  id: number
  score: number
  grade: string | null
  factorPoints: Record<string, number>
  current: boolean
  createdAt: string
}

export interface AuditEntry {
  id: number
  action: string
  actorId: number | null
  actorName: string
  before: Record<string, unknown> | null
  after: Record<string, unknown> | null
  createdAt: string
}

export interface Evidence {
  factor: string
  quote: string
}

export interface TriageResult {
  requestType: string
  category: string
  /** "학생 포털 DB 컬럼 추가" 같은 20자 이내 요약. Validator 가 폴백해 항상 채워져 있다. */
  title: string | null
  environment: string | null
  requestedTime: string | null
  recommendedPriority: string
  recommendedTeam: string
  affectedService: string | null
  testPlanProvided: boolean | null
  rollbackPlanProvided: boolean | null
  missingFields: string[]
  riskFactors: string[]
  evidence: Evidence[]
  clarifyingQuestions: string[]
  confidence: number
  corrections: string[] | null
}

export type RiskGrade = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface TriageResponse {
  attemptNo: number
  resultStatus: 'SUCCESS' | 'INVALID_JSON' | 'FAILED'
  modelName: string
  confidence: number | null
  needsReview: boolean
  result: TriageResult | null
  message: string | null
  ticketStatus: string
  slaPaused: boolean
  /** CHANGE 요청만 계산됨. 그 외(장애·문의 등)는 null. */
  riskScore: number | null
  riskGrade: RiskGrade | null
  /** 요인 → 점수 분해 (화면 요인 행 오른쪽 "+3" 용). */
  factorPoints: Record<string, number> | null
}

/* 어시스턴트 /chat */

export interface ChatHistoryMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface ChatRequest {
  message: string
  history: ChatHistoryMessage[]
}

export interface ToolCallSummary {
  name: string
  args: Record<string, unknown>
  ok: boolean
  errorMessage?: string | null
}

export interface AssistantCardCreate {
  ticket: TicketCreatedResponse
  triage: TriageResponse
}

export interface AssistantCardGet {
  ticket: TicketDetailResponse
  triage?: TriageResponse
}

/** POST /api/tickets/{id}/supplement 응답 (= 재분류 후 ticket + triage). */
export interface SupplementResponse {
  ticket: TicketDetailResponse
  triage: TriageResponse
}

export interface SupplementRequest {
  field: string
  answer: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export type AssistantCard =
  | { tool: 'create_request'; data: AssistantCardCreate }
  | { tool: 'list_my_requests'; data: PageResponse<TicketSummaryResponse> }
  | { tool: 'list_requests'; data: PageResponse<TicketSummaryResponse> }
  | { tool: 'get_request'; data: AssistantCardGet }
  | { tool: string; data: unknown }

export interface ChatResponse {
  reply: string
  cards: AssistantCard[]
  toolCalls: ToolCallSummary[]
  truncated: boolean
}
