import { useEffect, useRef, useState } from 'react'
import { motion, useInView } from 'framer-motion'
import Reveal from './Reveal'

/*
 * 사용 방법 — 4단계가 오른쪽으로 갈수록 한 칸씩 올라가는 계단 배치.
 * 영역이 보이면 (1) 계단 가로선이 왼쪽부터 그려지고, (2) 단계 01~04가 0.3초 간격으로 등장.
 */

const STEPS = [
  { n: '01', title: '대화로 요청', body: '양식 없이 평소 말하듯 적습니다.' },
  { n: '02', title: '요청서 자동 작성', body: '빠진 정보는 먼저 묻습니다.' },
  { n: '03', title: '확인 후 접수', body: '접수하기를 눌러야 등록됩니다.' },
  { n: '04', title: '처리 현황 확인', body: '대화로 진행 상황을 확인합니다.' },
]

export default function StepsSection() {
  const stairRef = useRef<HTMLDivElement>(null)
  const inView = useInView(stairRef, { once: true, amount: 0.3 })
  const [play, setPlay] = useState(false)
  useEffect(() => {
    if (inView) setPlay(true)
  }, [inView])

  return (
    <section id="steps" className="w-full pt-10 pb-[160px]">
      <div className="mx-auto max-w-[1440px] px-16">
        <div className="flex justify-between items-end mb-14">
          <Reveal>
            <h2 className="text-[40px] leading-[1.3] font-bold tracking-[-0.02em]">
              요청부터 접수까지
              <br />네 단계
            </h2>
          </Reveal>
          <Reveal className="text-sm text-mute">사용 방법</Reveal>
        </div>

        <div ref={stairRef} className="relative h-[430px]">
          <svg
            viewBox="0 0 1000 430"
            preserveAspectRatio="none"
            className="absolute inset-0 w-full h-full"
          >
            {/* 아래쪽부터 오른쪽 위로 올라가는 계단 선 */}
            <motion.path
              d="M0 428H245V324H500V220H755V116H1000"
              fill="none"
              stroke="#111"
              strokeWidth={2}
              vectorEffect="non-scaling-stroke"
              initial={{ pathLength: 0 }}
              animate={play ? { pathLength: 1 } : { pathLength: 0 }}
              transition={{ duration: 1.6, ease: [0.65, 0, 0.35, 1] }}
            />
          </svg>
          {STEPS.map((s, i) => (
            <motion.div
              key={s.n}
              className="absolute w-[23%]"
              style={{
                left: `${i * 25.5}%`,
                bottom: 16 + i * 104,
              }}
              initial={{ opacity: 0, y: 14 }}
              animate={play ? { opacity: 1, y: 0 } : { opacity: 0, y: 14 }}
              transition={{
                duration: 0.5,
                delay: 0.35 + i * 0.3,
                ease: [0.2, 0.7, 0.2, 1],
              }}
            >
              <div className="text-[15px] text-faint">{s.n}</div>
              <strong className="block text-[22px] font-bold mt-1 mb-1.5">{s.title}</strong>
              <p className="text-base text-body leading-[1.6]">{s.body}</p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}
