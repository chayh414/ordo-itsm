import { motion } from 'framer-motion'

/*
 * Ordo 로고 — 원본 logo.svg (viewBox 910x696, 5 paths)의 벡터를 그대로 사용.
 * - 계단은 원본의 '채워진 도형' 대신 중심선 1-stroke (width 32, butt/miter) 로 교체해
 *   stroke-dashoffset 애니메이션으로 '그려지는' 효과를 낸다 (렌더 결과는 원본과 동일).
 * - 글자 4개 (O·R·D·O) 는 원본 path 그대로 사용. 애니메이션 순서는
 *   아래 O → R → D → 위 O (계단이 왼쪽 아래부터 오른쪽 위로 그려지는 것과 동기).
 */

const STAIR_CENTER_D =
  'M0 680.18 H235.72 V500.91 H455 V321.64 H674.28 V142.36 H910'

const LETTER_PATHS = [
  // 1. 아래 O
  'M154.053 599.732C154.053 628.982 136.303 646.232 112.553 646.232C88.8027 646.232 71.0527 628.857 71.0527 599.732C71.0527 570.482 88.8027 553.232 112.553 553.232C136.303 553.232 154.053 570.482 154.053 599.732ZM137.678 599.732C137.678 579.107 127.303 567.857 112.553 567.857C97.8027 567.857 87.4277 579.107 87.4277 599.732C87.4277 620.357 97.8027 631.607 112.553 631.607C127.303 631.607 137.678 620.357 137.678 599.732Z',
  // 2. R
  'M304.965 465.709V375.209H338.965C359.715 375.209 370.84 386.834 370.84 404.084C370.84 416.396 365.215 425.521 354.59 429.834L374.09 465.709H355.965L338.215 432.584H321.215V465.709H304.965ZM321.215 418.959H336.465C348.715 418.959 354.215 413.709 354.215 404.084C354.215 394.459 348.715 388.834 336.465 388.834H321.215V418.959Z',
  // 3. D
  'M557.071 287.491H526.321V196.991H557.571C584.571 196.991 600.946 213.991 600.946 242.116C600.946 270.366 584.571 287.491 557.071 287.491ZM542.571 273.366H556.196C575.071 273.366 584.696 262.991 584.696 242.116C584.696 221.366 575.071 211.116 556.696 211.116H542.571V273.366Z',
  // 4. 위 O
  'M831.619 64.0227C831.619 93.2727 813.869 110.523 790.119 110.523C766.369 110.523 748.619 93.1477 748.619 64.0227C748.619 34.7727 766.369 17.5227 790.119 17.5227C813.869 17.5227 831.619 34.7727 831.619 64.0227ZM815.244 64.0227C815.244 43.3977 804.869 32.1477 790.119 32.1477C775.369 32.1477 764.994 43.3977 764.994 64.0227C764.994 84.6477 775.369 95.8977 790.119 95.8977C804.869 95.8977 815.244 84.6477 815.244 64.0227Z',
]

interface LogoProps {
  /** 가로 폭(px). 비율 유지. */
  width?: number
  /** 계단 선이 그려지고 글자 4개가 차례로 올라오는 진입 애니메이션 (기본 false). */
  animated?: boolean
  className?: string
  'aria-label'?: string
}

export default function Logo({
  width = 120,
  animated = false,
  className,
  'aria-label': ariaLabel = 'Ordo',
}: LogoProps) {
  const stairDuration = 1.6
  const letterStagger = 0.36
  const stairDelay = 0.3

  return (
    <svg
      viewBox="0 0 910 696"
      width={width}
      className={className}
      role="img"
      aria-label={ariaLabel}
      xmlns="http://www.w3.org/2000/svg"
    >
      {/* 계단 — 중심선 1-stroke, 애니메이션은 stroke-dashoffset */}
      <motion.path
        d={STAIR_CENTER_D}
        fill="none"
        stroke="#111"
        strokeWidth={32}
        strokeLinecap="butt"
        strokeLinejoin="miter"
        initial={animated ? { pathLength: 0 } : false}
        animate={animated ? { pathLength: 1 } : undefined}
        transition={
          animated
            ? { duration: stairDuration, delay: stairDelay, ease: [0.65, 0, 0.35, 1] }
            : undefined
        }
      />

      {/* 글자 — 아래 O → R → D → 위 O 순 */}
      {LETTER_PATHS.map((d, i) => (
        <motion.path
          key={i}
          d={d}
          fill="#111"
          initial={animated ? { opacity: 0, y: 14 } : false}
          animate={animated ? { opacity: 1, y: 0 } : undefined}
          transition={
            animated
              ? {
                  duration: 0.5,
                  delay: stairDelay + 0.25 + i * letterStagger,
                  ease: [0.2, 0.7, 0.2, 1],
                }
              : undefined
          }
        />
      ))}
    </svg>
  )
}
