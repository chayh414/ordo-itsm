import { useNavigate, NavLink } from 'react-router-dom'
import { useAuth } from '../../lib/auth'
import { useFontSize } from '../../lib/useFontSize'

/*
 * 상단 바 64px (글자 크기에 따라 비례 scaling 됨 — h-16 = 4rem).
 * 왼쪽: 작은 로고 + 메뉴.
 * 오른쪽: 글자 크기 토글 ("가 가") · 사용자명 · 소속 · 로그아웃.
 */

const NAV = [
  { to: '/app', label: '어시스턴트' },
  { to: '/app/my', label: '내 요청' },
  { to: '/app/back', label: '백오피스' },
]

export default function TopBar() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()
  const { size, setSize } = useFontSize()

  return (
    <header className="h-16 border-b border-line flex items-center justify-between px-10">
      <div className="flex items-center">
        <NavLink to="/app" aria-label="Ordo" className="flex items-center">
          {/* 로고도 rem 기반(h-10 = 2.5rem)으로 — 글자 크기 토글에 비례 scaling */}
          <img src="/logo.svg" alt="Ordo" className="block h-10 w-auto" />
        </NavLink>
        <nav className="flex items-center gap-7 ml-14 text-[0.9375rem] text-mute">
          {NAV.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end={n.to === '/app'}
              className={({ isActive }) =>
                isActive ? 'text-ink font-semibold' : 'hover:text-ink transition-colors'
              }
            >
              {n.label}
            </NavLink>
          ))}
        </nav>
      </div>
      <div className="flex items-center gap-5 text-[0.875rem] text-mute">
        {/* 글자 크기 토글 — "가 가" (보통 / 크게) */}
        <div className="flex items-center gap-1.5" role="group" aria-label="글자 크기">
          <button
            type="button"
            onClick={() => setSize('normal')}
            aria-pressed={size === 'normal'}
            title="글자 크기 보통"
            className={
              'px-2 py-0.5 text-[0.9375rem] transition-colors ' +
              (size === 'normal' ? 'text-ink font-semibold' : 'text-mute hover:text-ink')
            }
          >
            가
          </button>
          <span className="text-faint" aria-hidden>|</span>
          <button
            type="button"
            onClick={() => setSize('large')}
            aria-pressed={size === 'large'}
            title="글자 크기 크게 (125%)"
            className={
              'px-2 py-0.5 text-[1.1875rem] transition-colors ' +
              (size === 'large' ? 'text-ink font-semibold' : 'text-mute hover:text-ink')
            }
          >
            가
          </button>
        </div>
        <span>
          <b className="text-ink font-semibold">{user?.name ?? ''}</b>
          {user?.tenantName ? ` · ${user.tenantName}` : ''}
        </span>
        <button
          type="button"
          onClick={() => {
            logout()
            navigate('/login', { replace: true })
          }}
          className="hover:text-ink transition-colors"
        >
          로그아웃
        </button>
      </div>
    </header>
  )
}
