import { useEffect, useState } from 'react'

/**
 * 전역 글자 크기 토글.
 * - 'normal' = 기본 (html font-size 100%)
 * - 'large'  = 125% (크게)
 *
 * html 루트 font-size 를 바꾸는 방식이라, 모든 rem 기반 크기가 함께 커진다.
 * 선택값은 localStorage 에 저장돼 다음 방문에도 유지.
 */

export type FontSize = 'normal' | 'large'

const STORAGE_KEY = 'ordo.fontSize'

function read(): FontSize {
  if (typeof window === 'undefined') return 'normal'
  return localStorage.getItem(STORAGE_KEY) === 'large' ? 'large' : 'normal'
}

function apply(size: FontSize) {
  if (typeof document === 'undefined') return
  document.documentElement.style.fontSize = size === 'large' ? '125%' : '100%'
}

export function useFontSize() {
  const [size, setSize] = useState<FontSize>(() => {
    const s = read()
    apply(s)
    return s
  })

  useEffect(() => {
    apply(size)
    localStorage.setItem(STORAGE_KEY, size)
  }, [size])

  return { size, setSize }
}

/** 앱 부팅 시 바로 적용 (toggle 컴포넌트가 마운트되기 전에도 크기 유지). main.tsx 에서 호출. */
export function initFontSizeFromStorage() {
  apply(read())
}
