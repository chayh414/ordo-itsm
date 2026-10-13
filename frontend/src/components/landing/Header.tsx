import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

/*
 * 상단 고정 헤더.
 * - 처음엔 투명 배경 + 선 없음
 * - 세로 스크롤 20px 넘으면 흰 배경(94% 반투명 + 블러) + 아래 가는 선
 * - 왼쪽: 작은 계단 로고(원본 svg 그대로), 오른쪽: 메뉴 3개 + 검정 로그인 버튼
 */
export default function Header() {
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 20)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  return (
    <header
      className={[
        'fixed top-0 left-0 right-0 z-20 transition-[background,border-color] duration-300 border-b',
        scrolled
          ? 'bg-white/95 border-line backdrop-blur-sm'
          : 'bg-transparent border-transparent',
      ].join(' ')}
    >
      <div className="mx-auto max-w-[1440px] px-16 h-[76px] flex items-center justify-between">
        <Link to="/" aria-label="Ordo 홈" className="flex items-center">
          <img src="/logo.svg" alt="Ordo" width={64} height={48} className="block" />
        </Link>
        <nav className="flex items-center gap-9 text-base text-body">
          <a href="#intro">소개</a>
          <a href="#steps">사용 방법</a>
          <a href="#security">보안</a>
          <Link
            to="/login"
            className="inline-flex items-center h-10 px-[18px] bg-accent hover:bg-accent-hover transition-colors text-white text-[15px] font-semibold"
          >
            로그인
          </Link>
        </nav>
      </div>
    </header>
  )
}
