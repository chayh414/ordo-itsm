import { Link } from 'react-router-dom'

export default function LoginPage() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center gap-6">
      <h1 className="text-2xl font-semibold">로그인</h1>
      <p className="text-mute">곧 디자인될 로그인 화면 자리입니다.</p>
      <Link to="/" className="text-sm underline underline-offset-4">
        ← 랜딩으로 돌아가기
      </Link>
    </div>
  )
}
