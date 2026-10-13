import { useState } from 'react'
import { useEventStream, type NotificationEvent } from '../../lib/useEventStream'
import NotificationToast from './NotificationToast'

/*
 * 토스트 쌓임 관리.
 * - 오른쪽 위 fixed, 최대 3개 (최신이 맨 위)
 * - 토스트 '확인' 클릭 → window custom event 'ordo:openTicket' 발행 → 상위 페이지가 상세 로드
 * - '닫기' → 해당 토스트 제거
 */

const MAX_TOASTS = 3

interface ActiveToast {
  id: string
  event: NotificationEvent
}

export default function ToastContainer() {
  const [toasts, setToasts] = useState<ActiveToast[]>([])

  useEventStream((ev) => {
    const id = `${ev.ticketId}:${ev.issuedAt}:${Date.now()}`
    setToasts(prev => [{ id, event: ev }, ...prev].slice(0, MAX_TOASTS))
  })

  function dismiss(id: string) {
    setToasts(prev => prev.filter(t => t.id !== id))
  }

  function open(toast: ActiveToast) {
    try {
      window.dispatchEvent(new CustomEvent('ordo:openTicket', {
        detail: { ticketId: toast.event.ticketId, ticketNo: toast.event.ticketNo },
      }))
    } catch { /* SSR */ }
    dismiss(toast.id)
  }

  if (toasts.length === 0) return null

  return (
    <div className="fixed top-20 right-10 z-50 flex flex-col gap-3"
         aria-live="polite" data-testid="toast-container">
      {toasts.map((toast) => (
        <NotificationToast
          key={toast.id}
          event={toast.event}
          onDismiss={() => dismiss(toast.id)}
          onOpen={() => open(toast)}
        />
      ))}
    </div>
  )
}
