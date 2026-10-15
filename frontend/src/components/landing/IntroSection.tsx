import Reveal from './Reveal'

export default function IntroSection() {
  return (
    <section id="intro" className="w-full py-[140px]">
      <div className="mx-auto max-w-[1440px] px-16 grid grid-cols-12 gap-6">
        <Reveal className="col-span-3 text-sm text-mute">소개</Reveal>
        <Reveal className="col-span-8 col-start-4">
          <p className="text-[32px] leading-[1.55] font-semibold tracking-[-0.02em]">
            고객사의 운영 요청을 대화로 등록하고, 처리 현황을 확인합니다. 빠진
            정보는 먼저 묻고, 위험한 변경은 사람이 확인한 뒤에만 접수합니다.
          </p>
          <a
            href="#steps"
            className="inline-block mt-[30px] text-base underline underline-offset-[5px]"
          >
            사용 방법 보기
          </a>
        </Reveal>
      </div>
    </section>
  )
}
