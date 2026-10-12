import { useNavigate, NavLink } from 'react-router-dom'
import { useAuth } from '../../lib/auth'

/*
 * 상단 바 64px. 왼쪽: 작은 로고 + 메뉴, 오른쪽: 사용자명 · 소속 · 로그아웃.
 * 로고는 랜딩에서 쓰던 원본 svg (애니메이션 없음).
 */

const NAV = [
  { to: '/app', label: '어시스턴트' },
  { to: '/app/my', label: '내 요청' },
  { to: '/app/back', label: '백오피스' },
]

export default function TopBar() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  return (
    <header className="h-16 border-b border-line flex items-center justify-between px-10">
      <div className="flex items-center">
        <NavLink to="/app" aria-label="Ordo" className="flex items-center">
          <img src="/logo.svg" alt="Ordo" width={56} height={42} className="block" />
        </NavLink>
        <nav className="flex items-center gap-7 ml-14 text-[15px] text-mute">
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
      <div className="flex items-center gap-5 text-[14px] text-mute">
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
