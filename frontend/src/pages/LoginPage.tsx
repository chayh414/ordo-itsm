import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import { ApiError } from '../lib/api'
import { usePageTitle } from '../lib/usePageTitle'

/*
 * /login — 디자인 가이드 v3 기준.
 * - 가로 중앙 정렬 좁은 폼
 * - 입력은 밑줄 하나 (박스 아님)
 * - 로그인 버튼 44px 검정
 * - 하단에 시연 계정 3개 빠른선택 (링크 형태)
 */

const DEMO_ACCOUNTS = [
  { label: '김요청 · A대학교 정보화팀 (요청자)', email: 'requester@a-univ.demo' },
  { label: '이운영 · 시스원 (운영 담당자)', email: 'operator@sysone.demo' },
  { label: '박관리 · 시스원 (운영 관리자)', email: 'admin@sysone.demo' },
]
const DEMO_PASSWORD = 'Password123!'

export default function LoginPage() {
  usePageTitle('Ordo')
  const navigate = useNavigate()
  const { login, token } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // 이미 로그인된 상태로 /login 에 오면 바로 /app 으로
  useEffect(() => {
    if (token) navigate('/app', { replace: true })
  }, [token, navigate])

  async function submit(e?: FormEvent) {
    e?.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(email.trim(), password)
      navigate('/app')
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
      navigate('/app')
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
              className="w-full border-b border-line pb-2 text-[0.9375rem] placeholder:text-faint focus:border-ink focus:outline-none transition-colors"
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
              className="w-full border-b border-line pb-2 text-[0.9375rem] placeholder:text-faint focus:border-ink focus:outline-none transition-colors"
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
            className="w-full h-11 bg-ink text-white text-[0.9375rem] font-semibold disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {busy ? '로그인 중…' : '로그인'}
          </button>
        </form>

        <div className="mt-14">
          <p className="text-[0.8125rem] text-mute mb-3">시연 계정 바로 로그인</p>
          <ul className="border-t border-line">
            {DEMO_ACCOUNTS.map((a) => (
              <li key={a.email} className="border-b border-line">
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => quickLogin(a.email)}
                  className="w-full text-left py-3 text-[0.875rem] hover:pl-2 transition-[padding] duration-300 disabled:opacity-40"
                >
                  {a.label}
                </button>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  )
}
