import { useEffect, useMemo, useState } from 'react'
import { Navigate } from 'react-router-dom'
import TopBar from '../components/app/TopBar'
import ToastContainer from '../components/app/ToastContainer'
import { useAuth } from '../lib/auth'
import { api } from '../lib/api'
import { usePageTitle } from '../lib/usePageTitle'
import { formatCorrection, label } from '../lib/i18n'
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
 * design-guide §3~5 — 가는 선·여백, 그림자/배지/이모지 없음.
 * 포인트 색(accent) 은 선택된 행 왼쪽 선·링크·버튼에만.
 */

const PROVIDER_ROLES = new Set(['OPERATOR', 'APPROVER', 'OPS_ADMIN'])

const ACTION_KO: Record<string, string> = {
  TICKET_CREATED: '요청 생성', AI_TRIAGED: 'AI 분석 완료', AI_TRIAGE_FAILED: 'AI 분석 실패',
  TRIAGE_CONFIRMED: '분류 확정', TICKET_UPDATED: '요청 수정',
  INFO_SUPPLEMENTED: '보완 저장', RISK_ASSESSED: '위험도 산정', RISK_REASSESSED: '위험도 재산정',
  TICKET_ASSIGNED: '담당자 배정', STATUS_CHANGED: '상태 변경',
  SLA_PAUSED: 'SLA 정지', SLA_RESUMED: 'SLA 재개',
  APPROVAL_REQUESTED: '승인 요청', APPROVED: '승인됨', REJECTED: '반려됨',
  COMMENT_ADDED: '의견 추가', LOGIN_FAILED: '로그인 실패', ACCESS_DENIED: '접근 거부',
  ASSISTANT_TOOL_CALLED: '어시스턴트 도구 호출', EVENT_RECEIVED: '이벤트 수신 (모니터링)',
  SLA_NEAR_NOTIFIED: 'SLA 임박 알림', DEMO_SLA_ADJUSTED: '시연용 SLA 조정',
}

function fmt(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`
}

/** AI 가 뽑은 요약(title) 우선. 없으면 subject, 그래도 없으면 원문 앞부분. */
function displayTitle(ticket: { subject: string; originalRequest?: string },
                      triage: TriageResponse | null): string {
  const t = triage?.result?.title
  if (t && t.trim()) return t.trim()
  if (ticket.subject && ticket.subject.trim()) return ticket.subject.trim()
  const raw = ticket.originalRequest ?? ''
  return raw.length > 40 ? raw.slice(0, 40) + '…' : raw
}

function listTitle(t: TicketSummaryResponse): string {
  // AI 요약 title 우선. 없으면 subject, 그래도 길면 60자로 자른다.
  const picked = (t.title && t.title.trim()) || t.subject || ''
  return picked.length > 60 ? picked.slice(0, 60) + '…' : picked
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

  useEffect(() => {
    if (!token || !isProvider) return
    setLoadingList(true)
    api<PageResponse<TicketSummaryResponse>>(
      '/api/tickets?size=50', { token })
      .then(res => setTickets(res.content))
      .finally(() => setLoadingList(false))
  }, [token, isProvider])

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

  const isIncident = detail?.requestType === 'INCIDENT'

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
            {tickets.map(t => {
              const selected = selectedId === t.id
              return (
                <li key={t.id} className="border-b border-line">
                  <button type="button"
                          onClick={() => setSelectedId(t.id)}
                          className={
                            'w-full text-left px-10 py-4 border-l-4 transition-colors hover:bg-soft ' +
                            (selected
                              ? 'border-l-accent bg-soft'
                              : 'border-l-transparent')
                          }>
                    <div className="flex items-start justify-between gap-4">
                      <div className="min-w-0">
                        <div className="text-[13px] text-mute tabular-nums">{t.ticketNo} · {t.tenantName}</div>
                        <div className="text-[15px] text-ink mt-1 truncate">{listTitle(t)}</div>
                      </div>
                      <div className="text-[13px] text-mute whitespace-nowrap">
                        {label.requestType(t.requestType) || '분류 전'}
                      </div>
                    </div>
                    <div className="mt-2 flex items-center gap-5 text-[13px] text-mute">
                      <span>{label.status(t.status)}</span>
                      {t.riskGrade && (
                        <span>위험도 <span className="text-ink">{label.riskGrade(t.riskGrade)}</span></span>
                      )}
                      {t.sla && (
                        <span>마감 <span className="tabular-nums">{fmt(t.sla.resolutionDueAt)}</span></span>
                      )}
                    </div>
                  </button>
                </li>
              )
            })}
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
                {detail.ticketNo} · {detail.tenant.name} · {label.status(detail.status)}
              </div>
              <h1 className="mt-2 text-[26px] font-bold tracking-[-0.01em] text-ink">
                {displayTitle(detail, triage)}
              </h1>
              <div className="mt-1 text-[13px] text-mute">
                요청자 {detail.requester.name} · 생성 <span className="tabular-nums">{fmt(detail.createdAt)}</span>
              </div>

              <Section title="요청 원문 (마스킹 적용)">
                <div className="whitespace-pre-wrap text-[14px] text-body leading-[1.7] font-sans">
                  {detail.originalRequest}
                </div>
              </Section>

              <Section title={isIncident ? '이벤트 정보' : 'AI 추출 결과'}>
                <KeyRow k="유형" v={label.requestType(detail.requestType)} />
                <KeyRow k="분류" v={label.category(detail.category)} />
                <KeyRow k="환경" v={label.environment(detail.environment)} />
                <KeyRow k="영향 서비스" v={detail.affectedService ?? '—'} />
                {triage?.result?.title && <KeyRow k="제목(요약)" v={triage.result.title} />}
                {triage?.result?.testPlanProvided !== null && triage?.result?.testPlanProvided !== undefined && (
                  <KeyRow k="테스트 계획" v={triage.result.testPlanProvided ? '있음' : '없음'} />
                )}
                {triage?.result?.rollbackPlanProvided !== null && triage?.result?.rollbackPlanProvided !== undefined && (
                  <KeyRow k="롤백 계획" v={triage.result.rollbackPlanProvided ? '있음' : '없음'} />
                )}
                {triage?.result?.missingFields && triage.result.missingFields.length > 0 && (
                  <KeyRow k="확인 필요" v={triage.result.missingFields.map(label.field).join(', ')} />
                )}
                {triage?.confidence !== null && triage?.confidence !== undefined && (
                  <KeyRow k="확신도" v={(triage.confidence * 100).toFixed(0) + '%'} />
                )}
              </Section>

              {triage?.result?.corrections && triage.result.corrections.length > 0 && (
                <Section title="자동 보정 기록">
                  <ul className="text-[14px] text-body leading-[1.8]">
                    {triage.result.corrections.map((c, i) => (
                      <li key={i} className="border-t border-line py-2">{formatCorrection(c)}</li>
                    ))}
                  </ul>
                </Section>
              )}

              <Section title="위험 평가 이력">
                {isIncident ? (
                  <p className="text-[14px] text-mute">장애는 위험 점수 대상이 아닙니다.</p>
                ) : riskHistory.length === 0 ? (
                  <p className="text-[14px] text-mute">위험도 산정 기록이 없습니다.</p>
                ) : (
                  <ul>
                    {riskHistory.map((h, i) => (
                      <li key={h.id} className="border-t border-line py-3">
                        <div className="flex items-center justify-between">
                          <div>
                            <span className="tabular-nums text-ink text-[15px]">{h.score}점</span>
                            {h.grade && <span className="ml-2 text-[13px] text-mute">({label.riskGrade(h.grade)})</span>}
                            {i === 0 && h.current && <span className="ml-2 text-[13px] text-mute">· 최신</span>}
                          </div>
                          <span className="text-[13px] text-mute tabular-nums">{fmt(h.createdAt)}</span>
                        </div>
                        {Object.keys(h.factorPoints).length > 0 && (
                          <div className="mt-1 text-[13px] text-mute">
                            {Object.entries(h.factorPoints)
                              .map(([k, v]) => `${label.riskFactor(k)} +${v}`).join(' · ')}
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
                    <KeyRow k={isIncident ? '1차 통보 기한 (응답)' : '응답 기한'} v={fmt(detail.sla.responseDueAt)} />
                    <KeyRow k="해결 기한" v={fmt(detail.sla.resolutionDueAt)} />
                    <KeyRow k="정지 상태" v={detail.sla.paused ? `정지 중 (누계 ${detail.sla.pausedTotalMinutes}분)` : '진행 중'} />
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
