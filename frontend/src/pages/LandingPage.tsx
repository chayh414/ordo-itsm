import { useEffect } from 'react'
import { MotionConfig } from 'framer-motion'
import Lenis from 'lenis'
import { usePageTitle } from '../lib/usePageTitle'
import Header from '../components/landing/Header'
import HeroSection from '../components/landing/HeroSection'
import IntroSection from '../components/landing/IntroSection'
import ScreensSection from '../components/landing/ScreensSection'
import StepsSection from '../components/landing/StepsSection'
import SecuritySection from '../components/landing/SecuritySection'
import Marquee from '../components/landing/Marquee'
import Footer from '../components/landing/Footer'

export default function LandingPage() {
  usePageTitle('Ordo')

  // Lenis 스무스 스크롤. prefers-reduced-motion 이면 활성화하지 않음.
  useEffect(() => {
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (reduced) return

    const lenis = new Lenis()
    let frame = 0
    const raf = (t: number) => {
      lenis.raf(t)
      frame = requestAnimationFrame(raf)
    }
    frame = requestAnimationFrame(raf)
    return () => {
      cancelAnimationFrame(frame)
      lenis.destroy()
    }
  }, [])

  return (
    // MotionConfig reducedMotion="user" — prefers-reduced-motion 이면 Framer 가 즉시 최종값으로 스냅.
    // Lenis 와 CSS 쪽 reduce-motion 처리와 함께, 모든 애니메이션 레이어가 일관되게 꺼진다.
    <MotionConfig reducedMotion="user">
      <Header />
      <main>
        <HeroSection />
        <IntroSection />
        <ScreensSection />
        <StepsSection />
        <SecuritySection />
        <Marquee />
      </main>
      <Footer />
    </MotionConfig>
  )
}
