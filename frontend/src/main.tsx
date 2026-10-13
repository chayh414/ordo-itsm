import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { AuthProvider } from './lib/auth'
import { initFontSizeFromStorage } from './lib/useFontSize'
import './index.css'
import App from './App.tsx'

// localStorage 에 저장된 글자 크기(normal/large)를 React 마운트 전에 html 에 적용 — 깜빡임 방지.
initFontSizeFromStorage()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
