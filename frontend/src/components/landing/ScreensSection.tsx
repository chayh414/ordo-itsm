import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import Reveal from './Reveal'
import { useAuth } from '../../lib/auth'

/*
 * 메뉴 — 3개 행이 수평선으로 구분됨.
 * 행에 마우스 올리면 해당 화면 캡처가 커서를 따라 떠오름.
 * 행을 클릭하면 역할에 맞는 /app 경로로 이동. 역할이 다르면 /login?as=&next= 로 가드.
 */

type As = 'requester' | 'operator'
type Row = { i: string; title: string; desc: string; img?: string; as: As; next: string }

const ROWS: Row[] = [
  {
    i: '01',
    title: '요청자 화면',
    desc: '대화로 요청을 등록하고 요청서를 확인합니다',
    img: '/screen-requester.png',
    as: 'requester',
    next: '/app',
  },
  {
    i: '02',
    title: '운영 담당자 화면',
    desc: '담당 요청을 조회하고 장애 알림을 받습니다',
    as: 'operator',
    next: '/app',
  },
  {
    i: '03',
    title: '백오피스',
    desc: '접수된 요청과 처리 기록을 확인합니다',
    as: 'operator',
    next: '/app/backoffice',
  },
]

const PROVIDER_ROLES = new Set(['OPERATOR', 'APPROVER', 'OPS_ADMIN'])

export default function ScreensSection() {
  const [hover, setHover] = useState<Row | null>(null)
  const [pos, setPos] = useState({ x: 0, y: 0 })
  const previewRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const { token, user } = useAuth()

  function goto(row: Row) {
    const currentIsProvider = !!(user && PROVIDER_ROLES.has(user.role))
    const match = row.as === 'operator' ? currentIsProvider : (!!user && !currentIsProvider)
    if (token && match) {
      navigate(row.next)
    } else {
      navigate(`/login?as=${row.as}&next=${encodeURIComponent(row.next)}`)
    }
  }

  return (
    <section className="w-full pt-10 pb-[150px]">
      <div className="mx-auto max-w-[1440px] px-16">
        <Reveal className="text-sm text-mute mb-[18px]">메뉴</Reveal>
        <div>
          {ROWS.map((row, idx) => (
            <Reveal key={row.i} delay={idx * 0.05}>
              <div
                onMouseEnter={() => setHover(row)}
                onMouseLeave={() => setHover(null)}
                onMouseMove={(e) => setPos({ x: e.clientX, y: e.clientY })}
                onClick={() => goto(row)}
                role="link"
                tabIndex={0}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); goto(row) } }}
                className={[
                  'group grid items-center cursor-pointer py-[30px] border-t border-line',
                  'transition-[padding] duration-[350ms] ease-[cubic-bezier(0.2,0.7,0.2,1)]',
                  'hover:pl-4 focus:pl-4 focus:outline-none',
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
