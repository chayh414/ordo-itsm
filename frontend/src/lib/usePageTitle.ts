import { useEffect } from 'react'

/**
 * 브라우저 탭 title 을 페이지 진입 시 설정. 언마운트 시 index.html 기본값으로 복구.
 * 각 라우트 컴포넌트에서 호출.
 */
export function usePageTitle(title: string) {
  useEffect(() => {
    const prev = document.title
    document.title = title
    return () => {
      document.title = prev
    }
  }, [title])
}
