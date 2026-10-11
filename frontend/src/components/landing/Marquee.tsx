import { Link } from 'react-router-dom'

/*
 * "Ordo 시작하기 →" 를 왼쪽으로 흐르는 큰 글자 마퀴.
 * - 기본 26초, 호버 60초(느려짐).
 * - CSS keyframes 는 index.css 대신 로컬 <style> 블록으로 처리 (한 곳에서만 쓰임).
 * - 전체 영역 클릭 시 /login 으로 이동.
 */
export default function Marquee() {
  const items = Array.from({ length: 6 })
  return (
    <Link to="/login" aria-label="Ordo 시작하기" className="block">
      <div className="border-t border-b border-line overflow-hidden whitespace-nowrap py-10 cursor-pointer group">
        <style>{`
          @keyframes mq-slide {
            to { transform: translateX(-50%); }
          }
          .mq-track { animation: mq-slide 26s linear infinite; }
          .mq-wrap:hover .mq-track { animation-duration: 60s; }
          @media (prefers-reduced-motion: reduce) {
            .mq-track { animation: none; }
          }
        `}</style>
        <div className="mq-wrap">
          <div className="mq-track inline-block">
            {items.map((_, i) => (
              <span
                key={i}
                className="text-[96px] font-bold tracking-[-0.04em] mr-16 inline-block"
              >
                Ordo 시작하기
                <i className="not-italic font-light ml-7">→</i>
              </span>
            ))}
          </div>
        </div>
      </div>
    </Link>
  )
}
