/*
 * 백엔드 API 공통 fetch 래퍼.
 * - UTF-8 바디 송수신
 * - 토큰이 있으면 Authorization 헤더 자동 추가
 * - 2xx 아니면 ApiError 던짐 (서버 ErrorResponse { message } 가 있으면 그걸 담음)
 */

const BASE = 'http://localhost:8080'

export class ApiError extends Error {
  readonly status: number
  readonly body?: unknown
  constructor(status: number, message: string, body?: unknown) {
    super(message)
    this.status = status
    this.body = body
  }
}

export interface ApiRequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
  token?: string | null
  signal?: AbortSignal
}

export async function api<T = unknown>(path: string, opts: ApiRequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json; charset=utf-8'
  if (opts.token) headers['Authorization'] = `Bearer ${opts.token}`

  const res = await fetch(`${BASE}${path}`, {
    method: opts.method ?? (opts.body !== undefined ? 'POST' : 'GET'),
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    signal: opts.signal,
  })

  const text = await res.text()
  const json = text ? safeJson(text) : undefined

  if (!res.ok) {
    let message = `HTTP ${res.status}`
    if (json && typeof json === 'object' && 'message' in json) {
      const raw = (json as Record<string, unknown>).message
      if (typeof raw === 'string' && raw.length > 0) message = raw
    }
    // 401 → 전역 핸들러에 통지 (App 에서 logout + /login + 안내)
    // 로그인 자체는 /api/auth/login 경로이므로 그 경로는 제외해 재입력 중 알림 폭주 방지.
    if (res.status === 401 && !path.startsWith('/api/auth/login')) {
      try { window.dispatchEvent(new CustomEvent('ordo:unauthorized')) } catch { /* SSR */ }
    }
    throw new ApiError(res.status, message, json)
  }

  return json as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}
