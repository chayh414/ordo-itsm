import { useEffect, useRef } from 'react'
import { fetchEventSource, type EventSourceMessage } from '@microsoft/fetch-event-source'
import { useAuth } from './auth'

/*
 * 실시간 알림 SSE 구독 훅.
 * - fetch-event-source 로 Authorization 헤더를 그대로 보냄 (EventSource 는 헤더 미지원).
 * - 연결 끊기면 라이브러리가 자동 재연결 (onopen 에서 throw 하지 않는 한).
 * - 401 이면 전역 'ordo:unauthorized' 이벤트 발생 → App 이 로그아웃·로그인 리다이렉트.
 * - 로그아웃·토큰 변경 시 기존 구독을 abort 하고 새로 연결.
 */

export interface NotificationEvent {
  type: 'INCIDENT_CREATED' | 'SLA_NEAR'
  ticketId: number
  ticketNo: string
  tenantId: number
  tenantName: string
  requesterId: number | null
  title: string
  summary: string
  dueAt: string          // ISO OffsetDateTime
  issuedAt: string
}

const BASE = 'http://localhost:8080'

class FatalCloseError extends Error {}

export function useEventStream(onNotification: (ev: NotificationEvent) => void) {
  const { token } = useAuth()
  const handlerRef = useRef(onNotification)
  handlerRef.current = onNotification

  useEffect(() => {
    if (!token) return
    const ctrl = new AbortController()

    void fetchEventSource(`${BASE}/api/events/stream`, {
      signal: ctrl.signal,
      headers: { Authorization: `Bearer ${token}` },
      openWhenHidden: true,       // 백그라운드 탭에서도 유지 (알림 받기 위함)
      async onopen(res) {
        if (res.ok && (res.headers.get('content-type') ?? '').includes('text/event-stream')) {
          return
        }
        if (res.status === 401) {
          try { window.dispatchEvent(new CustomEvent('ordo:unauthorized')) } catch { /* SSR */ }
          throw new FatalCloseError('unauthorized')
        }
        // 그 외 비정상 응답 → 라이브러리가 재연결 시도하도록 그냥 throw 하지 않음
      },
      onmessage(msg: EventSourceMessage) {
        if (msg.event !== 'notification' || !msg.data) return
        try {
          const payload = JSON.parse(msg.data) as NotificationEvent
          handlerRef.current(payload)
        } catch {
          // 잘못된 payload 는 조용히 무시
        }
      },
      onerror(err) {
        if (err instanceof FatalCloseError) throw err
        // 그 외 네트워크 오류 → 자동 재연결 (라이브러리 기본). 재연결 간격 반환으로 조정 가능.
        return 2000
      },
    }).catch(() => {
      // FatalCloseError 등 — 재연결 중단. 다음 토큰 변경 시 effect 재실행.
    })

    return () => ctrl.abort()
  }, [token])
}
