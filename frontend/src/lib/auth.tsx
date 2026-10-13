import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from './api'

/*
 * 로그인 세션 관리.
 * - 토큰과 사용자 정보는 sessionStorage 에 (탭 닫으면 사라짐)
 * - useAuth() 로 읽고 login()/logout() 으로 조작
 */

export interface AuthUser {
  userId: number
  email: string
  name: string
  role: string
  tenantName: string
  tenantId: number
}

interface LoginResponse {
  accessToken: string
}

interface MeResponse {
  id: number
  email: string
  name: string
  role: string
  tenant: { id: number; code: string; name: string }
}

interface AuthState {
  token: string | null
  user: AuthUser | null
}

interface AuthContextValue extends AuthState {
  login(email: string, password: string): Promise<void>
  logout(): void
}

const AuthContext = createContext<AuthContextValue | null>(null)

const STORAGE_KEY = 'ordo.auth'

function read(): AuthState {
  if (typeof window === 'undefined') return { token: null, user: null }
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return { token: null, user: null }
    return JSON.parse(raw) as AuthState
  } catch {
    return { token: null, user: null }
  }
}

function write(state: AuthState) {
  if (state.token) {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } else {
    sessionStorage.removeItem(STORAGE_KEY)
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(() => read())

  useEffect(() => {
    // 다른 탭에서 로그아웃·로그인이 일어나면 동기화 (sessionStorage 는 탭간 공유 X 지만 안전차)
    const onStorage = () => setState(read())
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    ...state,
    async login(email, password) {
      const res = await api<LoginResponse>('/api/auth/login', { body: { email, password } })
      const me = await api<MeResponse>('/api/auth/me', { token: res.accessToken })
      const user: AuthUser = {
        userId: me.id,
        email: me.email,
        name: me.name,
        role: me.role,
        tenantId: me.tenant.id,
        tenantName: me.tenant.name,
      }
      const next: AuthState = { token: res.accessToken, user }
      write(next)
      setState(next)
    },
    logout() {
      // 사용자별 대화·요청서 세션 캐시도 함께 정리.
      try {
        for (let i = sessionStorage.length - 1; i >= 0; i--) {
          const k = sessionStorage.key(i)
          if (k && k.startsWith('ordo.session.')) sessionStorage.removeItem(k)
        }
      } catch { /* ignore */ }
      write({ token: null, user: null })
      setState({ token: null, user: null })
    },
  }), [state])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
