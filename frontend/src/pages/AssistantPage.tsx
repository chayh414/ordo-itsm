import { Link } from 'react-router-dom'

export default function AssistantPage() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center gap-6">
      <h1 className="text-2xl font-semibold">어시스턴트</h1>
      <p className="text-mute">곧 구현될 어시스턴트 화면 자리입니다.</p>
      <Link to="/" className="text-sm underline underline-offset-4">
        ← 랜딩으로 돌아가기
      </Link>
    </div>
  )
}
