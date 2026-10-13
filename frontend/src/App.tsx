import { useEffect, useRef } from 'react'
import { Route, Routes, useNavigate } from 'react-router-dom'
import { useAuth } from './lib/auth'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import AssistantPage from './pages/AssistantPage'
import BackofficePage from './pages/BackofficePage'

export default function App() {
  const navigate = useNavigate()
  const { logout, token } = useAuth()
  const notifiedRef = useRef(false)

  // 전역 401 핸들러 — api.ts 가 디스패치하는 'ordo:unauthorized' 를 받아 로그아웃 + /login.
  // 짧은 시간에 여러 요청이 동시에 401 을 받아도 안내는 1회만.
  useEffect(() => {
    const onUnauthorized = () => {
      if (notifiedRef.current) return
      if (!token) return   // 이미 로그아웃된 상태면 무시
      notifiedRef.current = true
      logout()
      alert('로그인 시간이 만료되었습니다. 다시 로그인해 주세요.')
      navigate('/login', { replace: true })
      // 다음 로그인 뒤 다시 받을 수 있도록 잠깐 뒤 리셋
      setTimeout(() => { notifiedRef.current = false }, 3000)
    }
    window.addEventListener('ordo:unauthorized', onUnauthorized)
    return () => window.removeEventListener('ordo:unauthorized', onUnauthorized)
  }, [logout, navigate, token])

  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/app" element={<AssistantPage />} />
      <Route path="/app/backoffice" element={<BackofficePage />} />
    </Routes>
  )
}
