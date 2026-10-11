import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import Logo from '../Logo'

/*
 * 히어로 — 화면 꽉 차게.
 * 왼쪽: 큰 Logo (계단 선 그려짐 + 글자 4개 순차 등장).
 * 오른쪽: 짧은 소개 문장 + 검정 '시작하기' 버튼.
 * 아래: 좌/우 작은 메타 2줄.
 */
export default function HeroSection() {
  return (
    <section className="w-full min-h-screen pt-[150px] pb-10 flex flex-col">
      {/*
       * 바깥 flex 로 세로 중앙 정렬, 안쪽 grid 는 items-end 로 로고·패널 바닥을 맞춤.
       * 결과: 두 블록이 묶여서 뷰포트 세로 중앙에 자리잡고, 그 안에서 로고 맨 아랫단 = 시작하기 버튼 바닥.
       */}
      <div className="mx-auto w-full max-w-[1440px] px-16 flex-1 flex items-center">
        <div className="w-full grid grid-cols-12 gap-6 items-end">
          <div className="col-span-7">
            <Logo width={640} animated className="block max-w-full" />
          </div>
          <motion.div
            className="col-span-4 col-start-9"
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.9, delay: 1.8, ease: [0.2, 0.7, 0.2, 1] }}
          >
            <p className="text-[22px] leading-[1.6] font-medium">
              시스원 백오피스용
              <br />
              사내 AI 어시스턴트입니다.
            </p>
            <Link
              to="/login"
              className="inline-flex items-center h-12 px-6 mt-7 bg-ink text-white text-base font-semibold"
            >
              시작하기
            </Link>
          </motion.div>
        </div>
      </div>
      <div className="mx-auto w-full max-w-[1440px] px-16 mt-14 flex justify-between text-sm text-mute">
        <span>시스원 연계 프로젝트 · 2026</span>
        <span>아래로 스크롤</span>
      </div>
    </section>
  )
}
