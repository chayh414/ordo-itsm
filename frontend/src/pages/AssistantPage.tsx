import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Navigate } from 'react-router-dom'
import TopBar from '../components/app/TopBar'
import ChatPanel, { type UiMessage } from '../components/app/ChatPanel'
import DocPanel from '../components/app/DocPanel'
import { useAuth } from '../lib/auth'
import { api, ApiError } from '../lib/api'
import { usePageTitle } from '../lib/usePageTitle'
import type {
  AssistantCard,
  AssistantCardCreate,
  ChatRequest,
  ChatResponse,
  TicketDetailResponse,
} from '../lib/types'

/*
 * /app — 어시스턴트 메인.
 * - 로그인 안 돼있으면 /login 으로 리다이렉트
 * - 상단 바 + 500px 대화 + 1fr 요청서
 * - history 는 stateless: 매번 그동안의 user/assistant 턴을 재전송
 * - 서버에서 받은 cards 중 가장 최근 것을 DocPanel 에 표시
 * - create_request 카드일 때만 접수하기/취소 버튼 활성
 */

const PROVIDER_ROLES = ['OPERATOR', 'APPROVER', 'OPS_ADMIN']

export default function AssistantPage() {
  usePageTitle('어시스턴트 · Ordo')
  const { token, user } = useAuth()

  // 모든 Hook 은 조건부 분기 전에 (리액트 Hook 규칙)
  const [messages, setMessages] = useState<UiMessage[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [card, setCard] = useState<AssistantCard | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const lastUserMessageRef = useRef<string | null>(null)

  const sendMessage = useCallback(
    async (text: string) => {
      if (!token) return
      lastUserMessageRef.current = text

      const history = messages.map((m) => ({ role: m.role, content: m.content }))
      // 사용자 턴을 UI 에 즉시 반영
      setMessages((prev) => [...prev, { role: 'user', content: text }])
      setLoading(true)
      setError(null)

      try {
        const payload: ChatRequest = { message: text, history }
        const res = await api<ChatResponse>('/api/assistant/chat', { body: payload, token })

        setMessages((prev) => [...prev, { role: 'assistant', content: res.reply }])

        // 마지막 카드를 선택해 요청서 패널에 표시 (한 턴에 여러 개면 가장 최근 결과가 우선)
        const lastCard = res.cards.length > 0 ? res.cards[res.cards.length - 1] : null
        if (lastCard) {
          setCard(lastCard)
          setSubmitted(lastCard.tool === 'create_request'
            ? (lastCard.data as AssistantCardCreate).ticket.status !== 'DRAFT'
            : false)
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
    // 실패한 턴의 사용자 메시지는 이미 messages 에 들어 있으므로 pop 후 재전송
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
  }, [])

  const onSubmit = useCallback(async () => {
    if (!token || !card || card.tool !== 'create_request') return
    const createCard = card.data as AssistantCardCreate
    setSubmitting(true)
    try {
      const detail = await api<TicketDetailResponse>(
        `/api/tickets/${createCard.ticket.id}/submit`,
        { method: 'POST', token },
      )
      // 로컬 카드 상태를 접수 후 상태로 갱신 (ticket 만 교체)
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
      // Ordo 응답으로 접수 안내 추가
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

  // 다른 카드가 로드되면 submitted 플래그 초기화
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

  if (!token || !user) return <Navigate to="/login" replace />

  return (
    <div className="h-screen flex flex-col overflow-hidden">
      <TopBar />
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
          isProviderSide={isProviderSide}
        />
      </main>
    </div>
  )
}
