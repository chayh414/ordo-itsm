import { useEffect, useState } from 'react'
import type { NotificationEvent } from '../../lib/useEventStream'

/*
 * 실시간 알림 토스트 1개.
 * - design-guide §5 알림: 화면 우측 상단, 흰 바탕 + 검정 1px 선, 둥근 모서리/그림자 없음
 * - 제목(고객사 · 영향 서비스) · 남은 시간(1초마다 업데이트) · "확인" 링크
 * - 포인트 색 없음, 이모지 없음. 숫자는 tabular-nums.
 */

interface Props {
  event: NotificationEvent
  onDismiss(): void
  onOpen(): void
}

function formatRemaining(ms: number): string {
  if (ms <= 0) return '0:00'
  const totalSec = Math.floor(ms / 1000)
  const m = Math.floor(totalSec / 60)
  const s = totalSec % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

function headlineOf(event: NotificationEvent): string {
  if (event.type === 'INCIDENT_CREATED') {
    return `${event.tenantName} · ${event.title}`
  }
  return 'SLA 마감이 임박했습니다'
}

function subtitleOf(event: NotificationEvent): string {
  if (event.type === 'INCIDENT_CREATED') {
    return event.summary
  }
  // SLA_NEAR: 요청 제목 + 번호
  return `${event.ticketNo} · ${event.title}`
}

function dueLabel(event: NotificationEvent): string {
  return event.type === 'INCIDENT_CREATED' ? '1차 통보 기한까지' : 'SLA 마감까지'
}

export default function NotificationToast({ event, onDismiss, onOpen }: Props) {
  const [remainingMs, setRemainingMs] = useState(() =>
    new Date(event.dueAt).getTime() - Date.now())

  useEffect(() => {
    const id = window.setInterval(() => {
      setRemainingMs(new Date(event.dueAt).getTime() - Date.now())
    }, 1000)
    return () => window.clearInterval(id)
  }, [event.dueAt])

  function handleOpen(e: React.MouseEvent) {
    e.preventDefault()
    onOpen()
  }

  return (
    <div className="w-[320px] border border-ink bg-bg px-4 py-3"
         role="status" aria-live="polite" data-testid="notification-toast">
      <div className="flex items-start justify-between gap-2">
        <div className="text-[15px] font-semibold text-ink">{headlineOf(event)}</div>
        <button onClick={onDismiss}
                className="text-[13px] text-mute hover:text-ink"
                aria-label="알림 닫기">닫기</button>
      </div>
      <div className="mt-1 text-[14px] text-body">{subtitleOf(event)}</div>
      <div className="mt-3 flex items-center justify-between text-[13px] text-mute">
        <span>
          {dueLabel(event)}{' '}
          <span className="tabular-nums text-ink">{formatRemaining(remainingMs)}</span>
        </span>
        <a href="#" onClick={handleOpen}
           className="underline underline-offset-2 text-ink">확인</a>
      </div>
    </div>
  )
}
