import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Navigate } from 'react-router-dom'
import TopBar from '../components/app/TopBar'
import ChatPanel, { type UiMessage } from '../components/app/ChatPanel'
import DocPanel from '../components/app/DocPanel'
import ToastContainer from '../components/app/ToastContainer'
import { useAuth } from '../lib/auth'
import { api, ApiError } from '../lib/api'
import { usePageTitle } from '../lib/usePageTitle'
import type {
  AssistantCard,
  AssistantCardCreate,
  AssistantCardGet,
  ChatRequest,
  ChatResponse,
  RiskGrade,
  SupplementResponse,
  TicketDetailResponse,
  TriageResponse,
} from '../lib/types'

/*
 * /app — 어시스턴트 메인.
 * - 로그인 안 돼있으면 /login 으로 리다이렉트
 * - 상단 바 + 500px 대화 + 1fr 요청서
 * - history 는 stateless: 매번 그동안의 user/assistant 턴을 재전송
 * - 서버에서 받은 cards 중 가장 최근 것을 DocPanel 에 표시
 * - create_request 카드일 때만 접수하기/취소 버튼 활성
 *
 * 세션 복원:
 *   - 대화 이력·현재 요청서 상태를 sessionStorage.ordo.session.{userId} 에 저장
 *   - 새로고침·HMR 재마운트 시 messages/supplementedAnswers/prevRisk 는 그대로 복원
 *   - 요청서는 저장된 ticketId 로 서버에서 다시 조회 (접수됐거나 점수가 바뀌었을 수 있음)
 *   - 로그아웃 시 세션 키 삭제 (auth.tsx 에서 처리)
 */

const PROVIDER_ROLES = ['OPERATOR', 'APPROVER', 'OPS_ADMIN']

function cardTicketId(c: AssistantCard | null): number | undefined {
  if (!c) return undefined
  if (c.tool === 'create_request') return (c.data as AssistantCardCreate).ticket.id
  if (c.tool === 'get_request') return (c.data as AssistantCardGet).ticket.id
  return undefined
}

export default function AssistantPage() {
  usePageTitle('Ordo')
  const { token, user } = useAuth()

  const [messages, setMessages] = useState<UiMessage[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [card, setCard] = useState<AssistantCard | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [supplementingField, setSupplementingField] = useState<string | null>(null)
  const [supplementedAnswers, setSupplementedAnswers] = useState<Record<string, string>>({})
  const [prevRisk, setPrevRisk] = useState<{ grade: RiskGrade | null; score: number | null } | null>(null)
  const lastUserMessageRef = useRef<string | null>(null)
  const restoredRef = useRef(false)

  const sessionKey = user ? `ordo.session.${user.userId}` : null

  const sendMessage = useCallback(
    async (text: string) => {
      if (!token) return
      lastUserMessageRef.current = text

      const history = messages.map((m) => ({ role: m.role, content: m.content }))
      setMessages((prev) => [...prev, { role: 'user', content: text }])
      setLoading(true)
      setError(null)

      try {
        const payload: ChatRequest = { message: text, history }
        const res = await api<ChatResponse>('/api/assistant/chat', { body: payload, token })

        setMessages((prev) => [...prev, { role: 'assistant', content: res.reply }])

        const lastCard = res.cards.length > 0 ? res.cards[res.cards.length - 1] : null
        if (lastCard) {
          setCard(lastCard)
          setSubmitted(lastCard.tool === 'create_request'
            ? (lastCard.data as AssistantCardCreate).ticket.status !== 'DRAFT'
            : false)
          // 새 카드 로드 시 보완 기록·이전 위험도 초기화
          setSupplementedAnswers({})
          setPrevRisk(null)
        }
      } catch (err) {
        const msg = err instanceof ApiError ? err.message : '네트워크 오류가 발생했습니다.'
        setError(msg)
      } finally {
        setLoading(false)
      }
    },
    [messages, token],
  )

  const retry = useCallback(() => {
    const text = lastUserMessageRef.current
    if (!text) return
    setMessages((prev) => {
      if (prev.length > 0 && prev[prev.length - 1].role === 'user') return prev.slice(0, -1)
      return prev
    })
    setError(null)
    void sendMessage(text)
  }, [sendMessage])

  const onCancel = useCallback(() => {
    setCard(null)
    setSubmitted(false)
    setSupplementedAnswers({})
    setPrevRisk(null)
  }, [])

  const onSupplement = useCallback(
    async (field: string, answer: string) => {
      if (!token || !card || card.tool !== 'create_request') return
      const createCard = card.data as AssistantCardCreate

      setPrevRisk({
        grade: createCard.triage.riskGrade,
        score: createCard.triage.riskScore,
      })
      setSupplementingField(field)

      try {
        const resp = await api<SupplementResponse>(
          `/api/tickets/${createCard.ticket.id}/supplement`,
          { body: { field, answer }, token },
        )
        setCard({
          tool: 'create_request',
          data: {
            ticket: {
              id: resp.ticket.id,
              ticketNo: resp.ticket.ticketNo,
              status: resp.ticket.status,
              sla: resp.ticket.sla,
              triage: null,
              createdAt: resp.ticket.createdAt,
            },
            triage: resp.triage,
          },
        })
        setSupplementedAnswers((prev) => ({ ...prev, [field]: answer }))
      } catch (err) {
        const msg = err instanceof ApiError ? err.message : '보완 저장 중 오류가 발생했습니다.'
        setError(msg)
      } finally {
        setSupplementingField(null)
      }
    },
    [card, token],
  )

  const onSubmit = useCallback(async () => {
    if (!token || !card || card.tool !== 'create_request') return
    const createCard = card.data as AssistantCardCreate
    setSubmitting(true)
    try {
      const detail = await api<TicketDetailResponse>(
        `/api/tickets/${createCard.ticket.id}/submit`,
        { method: 'POST', token },
      )
      setCard({
        tool: 'create_request',
        data: {
          ...createCard,
          ticket: {
            ...createCard.ticket,
            status: detail.status,
            sla: detail.sla,
          },
        },
      })
      setSubmitted(true)
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: `요청 ${detail.ticketNo} 를 접수했습니다. 현재 상태는 "${detail.status}" 입니다.`,
        },
      ])
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : '접수 중 오류가 발생했습니다.'
      setError(msg)
    } finally {
      setSubmitting(false)
    }
  }, [card, token])

  // --- 세션 저장: 상태 변경 시 sessionStorage 갱신 ----------------------
  useEffect(() => {
    if (!sessionKey) return
    const payload = {
      messages,
      cardTicketId: cardTicketId(card),
      supplementedAnswers,
      prevRisk,
    }
    try {
      sessionStorage.setItem(sessionKey, JSON.stringify(payload))
    } catch {
      // quota 초과 등 — 조용히 무시 (세션 복원 못 되는 정도)
    }
  }, [sessionKey, messages, card, supplementedAnswers, prevRisk])

  // --- 세션 복원 (사용자별 1회) ------------------------------------------
  //    저장된 ticketId 는 서버에서 최신 상태로 다시 조회해 카드로 교체.
  useEffect(() => {
    if (!token || !sessionKey || restoredRef.current) return
    restoredRef.current = true

    // 1) dev-only __seed.html 로 주입된 state 가 있으면 그걸 우선 적용.
    if (import.meta.env.DEV) {
      const seedRaw = sessionStorage.getItem('ordo.initState')
      if (seedRaw) {
        sessionStorage.removeItem('ordo.initState')
        try {
          const bin = atob(seedRaw)
          const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0))
          const json = new TextDecoder('utf-8').decode(bytes)
          const s = JSON.parse(json) as {
            messages?: UiMessage[]
            card?: AssistantCard
            prevRisk?: { grade: RiskGrade | null; score: number | null }
            supplementedAnswers?: Record<string, string>
          }
          if (s.messages) setMessages(s.messages)
          if (s.card) setCard(s.card)
          if (s.prevRisk) setPrevRisk(s.prevRisk)
          if (s.supplementedAnswers) setSupplementedAnswers(s.supplementedAnswers)
          return   // seed 가 있으면 session 복원은 건너뜀
        } catch {
          // bad seed → fall through
        }
      }
    }

    // 2) 사용자별 세션 복원
    const raw = sessionStorage.getItem(sessionKey)
    if (!raw) return
    try {
      const s = JSON.parse(raw) as {
        messages?: UiMessage[]
        cardTicketId?: number
        supplementedAnswers?: Record<string, string>
        prevRisk?: { grade: RiskGrade | null; score: number | null }
      }
      if (s.messages) setMessages(s.messages)
      if (s.supplementedAnswers) setSupplementedAnswers(s.supplementedAnswers)
      if (s.prevRisk) setPrevRisk(s.prevRisk)
      if (s.cardTicketId) void refetchCard(s.cardTicketId)
    } catch {
      // bad json — 삭제
      sessionStorage.removeItem(sessionKey)
    }

    async function refetchCard(ticketId: number) {
      try {
        const [ticket, triage] = await Promise.all([
          api<TicketDetailResponse>(`/api/tickets/${ticketId}`, { token: token! }),
          api<TriageResponse>(`/api/tickets/${ticketId}/triage`, { token: token! }),
        ])
        setCard({
          tool: 'create_request',
          data: {
            ticket: {
              id: ticket.id,
              ticketNo: ticket.ticketNo,
              status: ticket.status,
              sla: ticket.sla,
              triage: null,
              createdAt: ticket.createdAt,
            },
            triage,
          },
        })
      } catch {
        // 티켓이 삭제됐거나 접근 거부 → 세션 버리고 깨끗이
        if (sessionKey) sessionStorage.removeItem(sessionKey)
        setCard(null)
      }
    }
  }, [token, sessionKey])

  // 다른 카드가 로드되면 submitted 플래그 재계산
  useEffect(() => {
    if (card?.tool === 'create_request') {
      const t = (card.data as AssistantCardCreate).ticket
      setSubmitted(t.status !== 'DRAFT')
    } else {
      setSubmitted(false)
    }
  }, [card])

  const isProviderSide = useMemo(
    () => (user ? PROVIDER_ROLES.includes(user.role) : false),
    [user],
  )

  // 토스트 '확인' 클릭 → 해당 티켓을 DocPanel 에 get_request 카드로 로드
  useEffect(() => {
    if (!token) return
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<{ ticketId: number; ticketNo: string }>).detail
      if (!detail) return
      void (async () => {
        try {
          const ticket = await api<TicketDetailResponse>(`/api/tickets/${detail.ticketId}`, { token })
          let triage: TriageResponse | undefined
          try {
            triage = await api<TriageResponse>(`/api/tickets/${detail.ticketId}/triage`, { token })
          } catch {
            triage = undefined
          }
          setCard({
            tool: 'get_request',
            data: { ticket, triage } as AssistantCardGet,
          })
        } catch {
          // 접근 거부 등 — 조용히 무시 (토스트가 서버에서 걸러진 범위 외 티켓일 가능성)
        }
      })()
    }
    window.addEventListener('ordo:openTicket', handler)
    return () => window.removeEventListener('ordo:openTicket', handler)
  }, [token])

  if (!token || !user) return <Navigate to="/login" replace />

  return (
    <div className="h-screen flex flex-col overflow-hidden">
      <TopBar />
      <ToastContainer />
      <main
        className="grid min-h-0 flex-1"
        style={{ gridTemplateColumns: '500px 1fr' }}
      >
        <ChatPanel
          messages={messages}
          loading={loading}
          error={error}
          onSend={sendMessage}
          onRetry={retry}
        />
        <DocPanel
          card={card}
          requesterName={user.name}
          submitting={submitting}
          submitted={submitted}
          onCancel={onCancel}
          onSubmit={onSubmit}
          onSupplement={onSupplement}
          supplementingField={supplementingField}
          supplementedAnswers={supplementedAnswers}
          prevRisk={prevRisk}
          isProviderSide={isProviderSide}
        />
      </main>
    </div>
  )
}
