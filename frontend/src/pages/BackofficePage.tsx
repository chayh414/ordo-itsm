import { useEffect, useMemo, useState } from 'react'
import { Navigate } from 'react-router-dom'
import TopBar from '../components/app/TopBar'
import ToastContainer from '../components/app/ToastContainer'
import { useAuth } from '../lib/auth'
import { api } from '../lib/api'
import { usePageTitle } from '../lib/usePageTitle'
import type {
  AuditEntry,
  PageResponse,
  RiskHistoryEntry,
  TicketDetailResponse,
  TicketSummaryResponse,
  TriageResponse,
} from '../lib/types'

/*
 * /app/backoffice — 운영 담당자·승인자·관리자 전용 읽기 전용 백오피스.
 * 요청자(REQUESTER)·고객사 관리자(CUSTOMER_ADMIN) 는 접근 시 /app 리다이렉트
 * (서버도 BackofficeController 에서 ACCESS_DENIED 로 거부함).
 *
 * 승인·상태 변경 버튼은 없음. 상단에 "시스원 백오피스를 대신하는 시연용 화면" 안내 한 줄.
 * 디자인은 design-guide §4~5 — 가는 선과 여백, 그림자/배지/이모지/포인트색 없음.
 */

const PROVIDER_ROLES = new Set(['OPERATOR', 'APPROVER', 'OPS_ADMIN'])
const STATUS_KO: Record<string, string> = {
  DRAFT: '요청서 초안', SUBMITTED: '접수됨', TRIAGED: '분류 완료', INFO_REQUIRED: '보완 대기',
  RISK_REVIEWED: '위험도 검토 완료', PENDING_APPROVAL: '승인 대기', APPROVED: '승인됨',
  REJECTED: '반려됨', ASSIGNED: '담당자 배정', SCHEDULED: '작업 예정', IN_PROGRESS: '처리 중',
  RESOLVED: '해결됨', COMPLETED: '완료', VERIFIED: '검증 완료', CLOSED: '종료', CANCELLED: '취소됨',
}
const REQUEST_TYPE_KO: Record<string, string> = {
  INCIDENT: '장애', SERVICE_REQUEST: '일반 요청', CHANGE: '변경 요청', INQUIRY: '문의',
}
const RISK_GRADE_KO: Record<string, string> = {
  LOW: '낮음', MEDIUM: '보통', HIGH: '높음', CRITICAL: '매우 높음',
}
const FACTOR_KO: Record<string, string> = {
  production_environment: '운영 환경',
  database_schema_change: 'DB 스키마 변경',
  database_permission_change: 'DB 권한 변경',
  firewall_change: '방화벽 변경',
  network_change: '네트워크 변경',
  rollback_plan_missing: '롤백 계획 없음',
  test_plan_missing: '테스트 계획 없음',
  night_work: '심야 작업',
}
const ACTION_KO: Record<string, string> = {
  TICKET_CREATED: '요청 생성', AI_TRIAGED: 'AI 분석 완료', AI_TRIAGE_FAILED: 'AI 분석 실패',
  INFO_SUPPLEMENTED: '보완 저장', RISK_ASSESSED: '위험도 산정', RISK_REASSESSED: '위험도 재산정',
  STATUS_CHANGED: '상태 변경', SLA_PAUSED: 'SLA 정지', SLA_RESUMED: 'SLA 재개',
  SLA_NEAR_NOTIFIED: 'SLA 임박 알림', EVENT_RECEIVED: '이벤트 수신 (모니터링)',
  DEMO_SLA_ADJUSTED: '시연용 SLA 조정',
  ACCESS_DENIED: '접근 거부', ASSISTANT_TOOL_CALLED: '어시스턴트 도구 호출',
}

function fmt(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`
}

export default function BackofficePage() {
  usePageTitle('Ordo · 백오피스')
  const { token, user } = useAuth()

  const [tickets, setTickets] = useState<TicketSummaryResponse[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [detail, setDetail] = useState<TicketDetailResponse | null>(null)
  const [triage, setTriage] = useState<TriageResponse | null>(null)
  const [riskHistory, setRiskHistory] = useState<RiskHistoryEntry[]>([])
  const [audit, setAudit] = useState<AuditEntry[]>([])
  const [loadingList, setLoadingList] = useState(false)

  const isProvider = useMemo(
    () => (user ? PROVIDER_ROLES.has(user.role) : false),
    [user],
  )

  // 목록 로드
  useEffect(() => {
    if (!token || !isProvider) return
    setLoadingList(true)
    api<PageResponse<TicketSummaryResponse>>(
      '/api/tickets?size=50', { token })
      .then(res => setTickets(res.content))
      .finally(() => setLoadingList(false))
  }, [token, isProvider])

  // 상세 로드
  useEffect(() => {
    if (!token || !selectedId) {
      setDetail(null); setTriage(null); setRiskHistory([]); setAudit([])
      return
    }
    let cancelled = false
    void (async () => {
      try {
        const [d, t, h, a] = await Promise.all([
          api<TicketDetailResponse>(`/api/tickets/${selectedId}`, { token }),
          api<TriageResponse>(`/api/tickets/${selectedId}/triage`, { token }).catch(() => null),
          api<RiskHistoryEntry[]>(`/api/backoffice/tickets/${selectedId}/risk-history`, { token }).catch(() => []),
          api<AuditEntry[]>(`/api/backoffice/tickets/${selectedId}/audit`, { token }).catch(() => []),
        ])
        if (cancelled) return
        setDetail(d); setTriage(t); setRiskHistory(h ?? []); setAudit(a ?? [])
      } catch {
        if (!cancelled) { setDetail(null); setTriage(null); setRiskHistory([]); setAudit([]) }
      }
    })()
    return () => { cancelled = true }
  }, [token, selectedId])

  if (!token || !user) return <Navigate to="/login" replace />
  if (!isProvider) return <Navigate to="/app" replace />

  return (
    <div className="h-screen flex flex-col overflow-hidden">
      <TopBar />
      <ToastContainer />
      <div className="px-10 py-3 border-b border-line text-[13px] text-mute">
        실제 시스원 백오피스를 대신하는 시연용 화면입니다. 승인·상태 변경 기능은 포함되지 않았습니다.
      </div>
      <main className="grid min-h-0 flex-1" style={{ gridTemplateColumns: '600px 1fr' }}>
        {/* 목록 */}
        <section className="min-h-0 overflow-auto border-r border-line">
          <div className="px-10 py-4 border-b border-line flex items-center justify-between">
            <h2 className="text-[15px] font-semibold text-ink">요청 목록</h2>
            <span className="text-[13px] text-mute">최신 {tickets.length}건</span>
          </div>
          {loadingList && (
            <div className="px-10 py-6 text-[14px] text-mute">불러오는 중…</div>
          )}
          <ul>
            {tickets.map(t => (
              <li key={t.id} className="border-b border-line">
                <button type="button"
                        onClick={() => setSelectedId(t.id)}
                        className={
                          'w-full text-left px-10 py-4 hover:bg-soft transition-colors ' +
                          (selectedId === t.id ? 'bg-soft' : '')
                        }>
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0">
                      <div className="text-[13px] text-mute tabular-nums">{t.ticketNo} · {t.tenantName}</div>
                      <div className="text-[15px] text-ink mt-1 truncate">{t.subject}</div>
                    </div>
                    <div className="text-[13px] text-mute whitespace-nowrap">
                      {t.requestType ? REQUEST_TYPE_KO[t.requestType] ?? t.requestType : '분류 전'}
                    </div>
                  </div>
                  <div className="mt-2 flex items-center gap-5 text-[13px] text-mute">
                    <span>{STATUS_KO[t.status] ?? t.status}</span>
                    {t.riskGrade && (
                      <span>위험도 <span className="text-ink">{RISK_GRADE_KO[t.riskGrade] ?? t.riskGrade}</span></span>
                    )}
                    {t.sla && (
                      <span>마감 <span className="tabular-nums">{fmt(t.sla.resolutionDueAt)}</span></span>
                    )}
                  </div>
                </button>
              </li>
            ))}
            {!loadingList && tickets.length === 0 && (
              <li className="px-10 py-10 text-[14px] text-mute">조건에 해당하는 요청이 없습니다.</li>
            )}
          </ul>
        </section>

        {/* 상세 */}
        <section className="min-h-0 overflow-auto">
          {!detail && (
            <div className="h-full flex items-center justify-center text-[14px] text-mute">
              왼쪽에서 요청을 선택하면 상세가 표시됩니다.
            </div>
          )}
          {detail && (
            <div className="px-10 py-8 max-w-[840px]">
              <div className="text-[13px] text-mute tabular-nums">
                {detail.ticketNo} · {detail.tenant.name} · {STATUS_KO[detail.status] ?? detail.status}
              </div>
              <h1 className="mt-2 text-[26px] font-bold tracking-[-0.01em] text-ink">
                {detail.subject}
              </h1>
              <div className="mt-1 text-[13px] text-mute">
                요청자 {detail.requester.name} · 생성 <span className="tabular-nums">{fmt(detail.createdAt)}</span>
              </div>

              <Section title="요청 원문 (마스킹 적용)">
                <pre className="whitespace-pre-wrap text-[14px] text-body leading-[1.7]">
                  {detail.originalRequest}
                </pre>
              </Section>

              <Section title="AI 추출 결과">
                <KeyRow k="유형" v={detail.requestType ? REQUEST_TYPE_KO[detail.requestType] ?? detail.requestType : '—'} />
                <KeyRow k="분류" v={detail.category ?? '—'} />
                <KeyRow k="환경" v={detail.environment ?? '—'} />
                <KeyRow k="영향 서비스" v={detail.affectedService ?? '—'} />
                {triage?.result?.title && <KeyRow k="제목(요약)" v={triage.result.title} />}
                {triage?.result?.testPlanProvided !== null && triage?.result?.testPlanProvided !== undefined && (
                  <KeyRow k="테스트 계획" v={triage.result.testPlanProvided ? '있음' : '없음'} />
                )}
                {triage?.result?.rollbackPlanProvided !== null && triage?.result?.rollbackPlanProvided !== undefined && (
                  <KeyRow k="롤백 계획" v={triage.result.rollbackPlanProvided ? '있음' : '없음'} />
                )}
                {triage?.result?.missingFields && triage.result.missingFields.length > 0 && (
                  <KeyRow k="확인 필요" v={triage.result.missingFields.join(', ')} />
                )}
                {triage?.confidence !== null && triage?.confidence !== undefined && (
                  <KeyRow k="확신도" v={(triage.confidence * 100).toFixed(0) + '%'} />
                )}
              </Section>

              {triage?.result?.corrections && triage.result.corrections.length > 0 && (
                <Section title="자동 보정 기록">
                  <ul className="text-[14px] text-body leading-[1.8] tabular-nums">
                    {triage.result.corrections.map((c, i) => (
                      <li key={i} className="border-t border-line py-2">{c}</li>
                    ))}
                  </ul>
                </Section>
              )}

              <Section title="위험 평가 이력">
                {riskHistory.length === 0 ? (
                  <p className="text-[14px] text-mute">위험도 산정 기록이 없습니다.</p>
                ) : (
                  <ul>
                    {riskHistory.map((h, i) => (
                      <li key={h.id} className="border-t border-line py-3">
                        <div className="flex items-center justify-between">
                          <div>
                            <span className="tabular-nums text-ink text-[15px]">{h.score}점</span>
                            {h.grade && <span className="ml-2 text-[13px] text-mute">({RISK_GRADE_KO[h.grade] ?? h.grade})</span>}
                            {i === 0 && h.current && <span className="ml-2 text-[13px] text-mute">· 최신</span>}
                          </div>
                          <span className="text-[13px] text-mute tabular-nums">{fmt(h.createdAt)}</span>
                        </div>
                        {Object.keys(h.factorPoints).length > 0 && (
                          <div className="mt-1 text-[13px] text-mute">
                            {Object.entries(h.factorPoints)
                              .map(([k, v]) => `${FACTOR_KO[k] ?? k} +${v}`).join(' · ')}
                          </div>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </Section>

              <Section title="SLA">
                {detail.sla ? (
                  <>
                    <KeyRow k="기준" v={detail.sla.basis} />
                    <KeyRow k="응답 기한" v={fmt(detail.sla.responseDueAt)} />
                    <KeyRow k="해결 기한" v={fmt(detail.sla.resolutionDueAt)} />
                    <KeyRow k="정지 상태" v={detail.sla.paused ? `정지 중 (누계 ${detail.sla.pausedTotalMinutes}분)` : '진행 중'} />
                    {detail.firstResponseDueAt && (
                      <KeyRow k="1차 통보 기한" v={fmt(detail.firstResponseDueAt)} />
                    )}
                  </>
                ) : (
                  <p className="text-[14px] text-mute">SLA 가 시작되지 않았습니다 (초안 또는 접수 전).</p>
                )}
              </Section>

              <Section title="감사 로그">
                {audit.length === 0 ? (
                  <p className="text-[14px] text-mute">기록이 없습니다.</p>
                ) : (
                  <ul>
                    {audit.map(a => (
                      <li key={a.id} className="border-t border-line py-2 text-[14px]">
                        <div className="flex items-center justify-between">
                          <span className="text-ink">{ACTION_KO[a.action] ?? a.action}</span>
                          <span className="text-[13px] text-mute tabular-nums">{fmt(a.createdAt)}</span>
                        </div>
                        <div className="text-[13px] text-mute">{a.actorName}</div>
                      </li>
                    ))}
                  </ul>
                )}
              </Section>
            </div>
          )}
        </section>
      </main>
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mt-10">
      <h2 className="text-[15px] font-semibold text-ink border-b border-line pb-2 mb-3">{title}</h2>
      {children}
    </section>
  )
}

function KeyRow({ k, v }: { k: string; v: string }) {
  return (
    <div className="grid grid-cols-[180px_1fr] py-2 border-t border-line text-[14px]">
      <span className="text-mute">{k}</span>
      <span className="text-body tabular-nums">{v}</span>
    </div>
  )
}
