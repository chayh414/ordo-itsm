import { useRef, useState } from 'react'
import Reveal from './Reveal'

/*
 * 화면 목록 — 3개 행이 수평선으로 구분됨.
 * 행에 마우스 올리면 해당 화면 캡처가 커서를 따라 떠오름.
 * - 01: screen-requester.png (실제 캡처)
 * - 02 · 03: "화면 준비 중" 회색 placeholder
 */

type Row = { i: string; title: string; desc: string; img?: string }

const ROWS: Row[] = [
  {
    i: '01',
    title: '요청자 화면',
    desc: '대화로 요청을 등록하고 요청서를 확인합니다',
    img: '/screen-requester.png',
  },
  {
    i: '02',
    title: '운영 담당자 화면',
    desc: '담당 요청을 조회하고 장애 알림을 받습니다',
  },
  {
    i: '03',
    title: '백오피스',
    desc: '접수된 요청과 처리 기록을 확인합니다',
  },
]

export default function ScreensSection() {
  const [hover, setHover] = useState<Row | null>(null)
  const [pos, setPos] = useState({ x: 0, y: 0 })
  const previewRef = useRef<HTMLDivElement>(null)

  return (
    <section className="w-full pt-10 pb-[150px]">
      <div className="mx-auto max-w-[1440px] px-16">
        <Reveal className="text-sm text-mute mb-[18px]">화면</Reveal>
        <div>
          {ROWS.map((row, idx) => (
            <Reveal key={row.i} delay={idx * 0.05}>
              <div
                onMouseEnter={() => setHover(row)}
                onMouseLeave={() => setHover(null)}
                onMouseMove={(e) => setPos({ x: e.clientX, y: e.clientY })}
                className={[
                  'group grid items-center cursor-pointer py-[30px] border-t border-line',
                  'transition-[padding] duration-[350ms] ease-[cubic-bezier(0.2,0.7,0.2,1)]',
                  'hover:pl-4',
                  idx === ROWS.length - 1 ? 'border-b border-line' : '',
                ].join(' ')}
                style={{ gridTemplateColumns: '80px 1fr 1.2fr 100px' }}
              >
                <span className="text-faint text-[15px]">{row.i}</span>
                <strong className="text-[28px] font-semibold tracking-[-0.02em]">
                  {row.title}
                </strong>
                <span className="text-mute text-[17px]">{row.desc}</span>
                <span className="text-right text-[15px]">보기 →</span>
              </div>
            </Reveal>
          ))}
        </div>
      </div>

      {/* 커서 따라가는 미리보기 카드. position:fixed 로 뷰포트 좌표 사용. */}
      <div
        ref={previewRef}
        className={[
          'fixed w-[420px] pointer-events-none z-[15] border border-line bg-white',
          'transition-[opacity,transform] duration-[250ms] ease-[cubic-bezier(0.2,0.7,0.2,1)]',
          hover ? 'opacity-100 scale-100' : 'opacity-0 scale-[0.96]',
        ].join(' ')}
        style={{
          left: pos.x + 28,
          top: pos.y - 120,
        }}
      >
        {hover?.img ? (
          <img src={hover.img} alt={hover.title} className="block w-full" />
        ) : (
          <div
            className="flex items-center justify-center text-faint text-sm bg-soft"
            style={{ aspectRatio: '1440 / 900' }}
          >
            화면 준비 중
          </div>
        )}
      </div>
    </section>
  )
}
