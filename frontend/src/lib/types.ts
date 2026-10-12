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
  createdAt: string
  updatedAt: string
}

export interface Evidence {
  factor: string
  quote: string
}

export interface TriageResult {
  requestType: string
  category: string
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
