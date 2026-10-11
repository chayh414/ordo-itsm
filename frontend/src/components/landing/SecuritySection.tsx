import Reveal from './Reveal'

export default function SecuritySection() {
  return (
    <section id="security" className="w-full py-[150px] border-t border-line">
      <div className="mx-auto max-w-[1440px] px-16 grid grid-cols-12 gap-6">
        <Reveal className="col-span-7">
          <p className="text-[52px] leading-[1.3] font-bold tracking-[-0.03em]">
            요청 내용은
            <br />
            회사 밖으로
            <br />
            나가지 않습니다.
          </p>
        </Reveal>
        <Reveal className="col-span-4 col-start-9 self-end text-[17px] text-body space-y-3.5">
          <p>분석은 사내 서버의 로컬 AI 가 처리합니다. 외부 AI 서비스로 내용을 보내지 않습니다.</p>
          <p>AI 는 요청서 작성과 조회만 할 수 있고, 승인이나 상태 변경은 할 수 없습니다.</p>
        </Reveal>
      </div>
    </section>
  )
}
