import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { ApiError } from '../lib/api'
import { usePageTitle } from '../lib/usePageTitle'

/*
 * /login — 디자인 가이드 v3 기준.
 * - 가로 중앙 정렬 좁은 폼
 * - 입력은 밑줄 하나 (박스 아님)
 * - 로그인 버튼 44px 파랑
 * - 하단에 시연 계정 3개 빠른선택
 * - /login?as=requester|operator&next=... 로 들어오면 해당 계정 강조 (자동 로그인 금지)
 * - 로그인 성공 시 next 로 이동 — next 는 /app 으로 시작하는 경로만 허용 (open redirect 방지)
 */

type As = 'requester' | 'operator'

const DEMO_ACCOUNTS: Array<{ label: string; email: string; as: As }> = [
  { label: '김요청 · A제조 정보시스템팀 (요청자)', email: 'requester@a-univ.demo', as: 'requester' },
  { label: '이운영 · 시스원 (운영 담당자)', email: 'operator@sysone.demo', as: 'operator' },
  { label: '박관리 · 시스원 (운영 관리자)', email: 'admin@sysone.demo', as: 'operator' },
]
const DEMO_PASSWORD = 'Password123!'

function safeNext(raw: string | null): string {
  if (!raw) return '/app'
  // open redirect 방지: /app 로 시작하는 경로만 허용 (외부 URL / // · \\ 차단)
  if (raw.startsWith('/app') && !raw.startsWith('//') && !raw.startsWith('/app/..')) return raw
  return '/app'
}

export default function LoginPage() {
  usePageTitle('Ordo')
  const navigate = useNavigate()
  const { login, token } = useAuth()
  const [searchParams] = useSearchParams()
  const asRole = (searchParams.get('as') as As | null) ?? null
  const nextPath = useMemo(() => safeNext(searchParams.get('next')), [searchParams])

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // 이미 로그인된 상태로 /login 에 오면 바로 next (혹은 /app) 로
  useEffect(() => {
    if (token) navigate(nextPath, { replace: true })
  }, [token, navigate, nextPath])

  async function submit(e?: FormEvent) {
    e?.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(email.trim(), password)
      navigate(nextPath)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '로그인에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  async function quickLogin(targetEmail: string) {
    setEmail(targetEmail)
    setPassword(DEMO_PASSWORD)
    setError(null)
    setBusy(true)
    try {
      await login(targetEmail, DEMO_PASSWORD)
      navigate(nextPath)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '로그인에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center px-6">
      <div className="w-full max-w-[23.75rem]">
        <Link to="/" className="block text-sm text-mute mb-10 underline underline-offset-4">
          ← 홈
        </Link>

        <h1 className="text-[1.75rem] font-bold tracking-[-0.02em] mb-10">로그인</h1>

        <form onSubmit={submit} className="space-y-7">
          <label className="block">
            <span className="block text-[0.8125rem] text-mute mb-2">이메일</span>
            <input
              type="email"
              required
              autoFocus
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="이메일 주소"
              className="w-full border-b border-line pb-2 text-[0.9375rem] placeholder:text-faint focus:border-accent focus:outline-none transition-colors"
            />
          </label>

          <label className="block">
            <span className="block text-[0.8125rem] text-mute mb-2">비밀번호</span>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder=""
              className="w-full border-b border-line pb-2 text-[0.9375rem] placeholder:text-faint focus:border-accent focus:outline-none transition-colors"
            />
          </label>

          {error && (
            <p className="text-[0.875rem]" style={{ color: 'var(--color-risk)' }}>
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={busy || !email || !password}
            className="w-full h-11 bg-accent hover:bg-accent-hover transition-colors text-white text-[0.9375rem] font-semibold disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {busy ? '로그인 중…' : '로그인'}
          </button>
        </form>

        <div className="mt-14">
          <p className="text-[0.8125rem] text-mute mb-3">
            {asRole === 'requester' && '요청자 계정으로 로그인'}
            {asRole === 'operator' && '운영 담당자 계정으로 로그인'}
            {!asRole && '시연 계정 바로 로그인'}
          </p>
          <ul className="border-t border-line">
            {DEMO_ACCOUNTS.map((a) => {
              const highlighted = asRole !== null && a.as === asRole
              return (
                <li key={a.email} className="border-b border-line">
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => quickLogin(a.email)}
                    className={[
                      'w-full text-left py-3 text-[0.875rem] hover:pl-2 transition-[padding] duration-300 disabled:opacity-40',
                      highlighted ? 'font-semibold text-ink' : 'text-body',
                    ].join(' ')}
                  >
                    {highlighted && <span aria-hidden className="mr-1 text-accent">●</span>}
                    {a.label}
                  </button>
                </li>
              )
            })}
          </ul>
        </div>
      </div>
    </div>
  )
}
