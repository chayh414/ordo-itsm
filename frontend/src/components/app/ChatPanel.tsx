import { useEffect, useRef, useState, type FormEvent } from 'react'

/*
 * 왼쪽 500px 대화창.
 * - 사용자 메시지: soft 배경 블록 (margin-left 40 으로 들여쓰기)
 * - Ordo 메시지: 배경 없이 "Ordo" 라벨 + 본문
 * - 상단에 날짜 라벨
 * - 하단 입력창 1줄 + 보내기 버튼
 * - 상태별 안내: 로딩("요청서를 작성하고 있습니다…"), 실패("AI가 준비 중입니다…" + 다시 시도)
 */

export interface UiMessage {
  role: 'user' | 'assistant'
  content: string
}

interface Props {
  messages: UiMessage[]
  loading: boolean
  error: string | null
  onSend(text: string): void
  onRetry(): void
}

export default function ChatPanel({ messages, loading, error, onSend, onRetry }: Props) {
  const [draft, setDraft] = useState('')
  const threadRef = useRef<HTMLDivElement>(null)

  // 새 메시지 들어오면 하단으로 자동 스크롤
  useEffect(() => {
    const el = threadRef.current
    if (!el) return
    el.scrollTop = el.scrollHeight
  }, [messages, loading])

  function submit(e: FormEvent) {
    e.preventDefault()
    const t = draft.trim()
    if (!t || loading) return
    onSend(t)
    setDraft('')
  }

  function todayLabel(): string {
    const d = new Date()
    const hh = d.getHours()
    const mm = String(d.getMinutes()).padStart(2, '0')
    const ap = hh >= 12 ? '오후' : '오전'
    const h12 = ((hh + 11) % 12) + 1
    return `오늘 ${ap} ${h12}:${mm}`
  }

  return (
    <section className="flex flex-col border-r border-line min-h-0">
      <div ref={threadRef} className="flex-1 overflow-y-auto px-10 pt-9 pb-6">
        <div className="text-[13px] text-faint mb-6">{todayLabel()}</div>

        {messages.map((m, i) =>
          m.role === 'user' ? (
            <div
              key={i}
              className="ml-10 mb-6 px-4 py-3.5 text-[15px] leading-[1.7]"
              style={{ background: 'var(--color-soft)' }}
            >
              {m.content}
            </div>
          ) : (
            <div key={i} className="mb-6">
              <div className="text-[13px] text-mute font-semibold mb-1">Ordo</div>
              <p className="text-[15.5px] leading-[1.75] whitespace-pre-wrap">{m.content}</p>
            </div>
          ),
        )}

        {loading && (
          <div className="mb-6">
            <div className="text-[13px] text-mute font-semibold mb-1">Ordo</div>
            <p className="text-[15.5px] leading-[1.75] text-mute">요청서를 작성하고 있습니다…</p>
          </div>
        )}

        {error && (
          <div className="mb-6">
            <div className="text-[13px] text-mute font-semibold mb-1">Ordo</div>
            <p className="text-[15.5px] leading-[1.75]">
              AI가 준비 중입니다. 잠시 후 다시 시도해 주세요.
            </p>
            <button
              type="button"
              onClick={onRetry}
              className="mt-2 text-[14px] underline underline-offset-4"
            >
              다시 시도
            </button>
          </div>
        )}
      </div>

      <form onSubmit={submit} className="border-t border-line px-10 py-4 flex items-center gap-4">
        <input
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="메시지를 입력하세요"
          disabled={loading}
          className="flex-1 text-[15px] placeholder:text-faint focus:outline-none disabled:opacity-50"
        />
        <button
          type="submit"
          disabled={loading || !draft.trim()}
          className="h-11 px-5 bg-ink text-white text-[15px] font-semibold disabled:opacity-40 disabled:cursor-not-allowed"
        >
          보내기
        </button>
      </form>
    </section>
  )
}
