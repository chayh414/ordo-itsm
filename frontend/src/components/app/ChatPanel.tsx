import { useEffect, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'

/*
 * 왼쪽 500px 대화창.
 * - 사용자 메시지: soft 배경 블록 (margin-left 40 으로 들여쓰기), 줄바꿈 유지 (whitespace: pre-wrap)
 * - Ordo 메시지: 배경 없이 "Ordo" 라벨 + 본문
 * - 상단에 날짜 라벨
 * - 하단 입력창은 textarea (카톡 스타일):
 *   · 내용에 맞게 높이 자동 증가, 최대 6줄 그 이상은 내부 스크롤
 *   · Enter = 보내기, Shift+Enter = 줄바꿈
 *   · 한글 조합 중 Enter 는 무시 (isComposing) — 마지막 글자 중복 전송 방지
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

const INPUT_LINE_HEIGHT = 24   // text-[15px] × 1.6 ≈ 24px (한 줄 높이, textarea sizing 기준)
const INPUT_MAX_LINES = 6
const INPUT_VERT_PADDING = 20  // py-2.5 상·하 합

export default function ChatPanel({ messages, loading, error, onSend, onRetry }: Props) {
  const [draft, setDraft] = useState('')
  const threadRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  // 새 메시지 들어오면 하단으로 자동 스크롤
  useEffect(() => {
    const el = threadRef.current
    if (!el) return
    el.scrollTop = el.scrollHeight
  }, [messages, loading])

  // draft 가 바뀔 때마다 textarea 높이 재계산 (scrollHeight 기반, 6줄 상한)
  useLayoutEffect(() => {
    const el = inputRef.current
    if (!el) return
    el.style.height = 'auto'
    const maxHeight = INPUT_LINE_HEIGHT * INPUT_MAX_LINES + INPUT_VERT_PADDING
    el.style.height = Math.min(el.scrollHeight, maxHeight) + 'px'
    el.style.overflowY = el.scrollHeight > maxHeight ? 'auto' : 'hidden'
  }, [draft])

  function submit(e?: FormEvent) {
    e?.preventDefault()
    const t = draft.trim()
    if (!t || loading) return
    onSend(t)
    setDraft('')
    // 높이 리셋은 useLayoutEffect 가 처리 (draft '' 로 바뀌면 자동)
  }

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key !== 'Enter') return
    // IME 조합 중 Enter 무시 — 한글 마지막 글자 중복 전송 방지
    if (e.nativeEvent.isComposing || e.keyCode === 229) return
    if (e.shiftKey) return                 // Shift+Enter 는 줄바꿈 (default 동작 유지)
    e.preventDefault()
    submit()
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
              className="ml-10 mb-6 px-4 py-3.5 text-[15px] leading-[1.7] whitespace-pre-wrap"
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

      <form onSubmit={submit} className="border-t border-line px-10 py-4 flex items-end gap-4">
        <textarea
          ref={inputRef}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder="메시지를 입력하세요 (Enter 보내기 · Shift+Enter 줄바꿈)"
          rows={1}
          disabled={loading}
          className="flex-1 resize-none text-[15px] leading-[1.6] py-2.5 placeholder:text-faint focus:outline-none disabled:opacity-50 bg-transparent"
          style={{ height: INPUT_LINE_HEIGHT + INPUT_VERT_PADDING + 'px' }}
        />
        <button
          type="submit"
          disabled={loading || !draft.trim()}
          className="h-11 px-5 bg-ink text-white text-[15px] font-semibold disabled:opacity-40 disabled:cursor-not-allowed shrink-0"
        >
          보내기
        </button>
      </form>
    </section>
  )
}
