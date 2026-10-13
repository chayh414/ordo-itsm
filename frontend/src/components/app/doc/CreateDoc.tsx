import { useState } from 'react'
import type { AssistantCardCreate, RiskGrade } from '../../../lib/types'
import { formatCorrection, formatRequestedTime, label } from '../../../lib/i18n'

/*
 * create_request 결과 요청서 (디자인 가이드 v3 + 사용자 재배치 요구).
 *
 * 섹션 순서 (알아야 할 것 → 해야 할 것 → 근거):
 *   ① 상단 메타 (상태 · 요청번호)
 *   ② 제목
 *   ③ 핵심 요약 한 줄 — "승인이 필요한 변경 · 위험도 매우 높음 (9점)" (HIGH 이상 risk 색)
 *   ④ 보완 질문 ("접수 전에 N가지 확인이 필요합니다" + 질문·입력창)
 *   ⑤ 위험 요인 (점수 높은 순)
 *   ⑥ 요청 정보 표
 *   ⑦ 자동 보정 (접힘 기본)
 *   ⑧ 하단 안내 + 취소 · 접수하기
 *
 * 글자 크기: 본문 1.0625rem, 라벨 0.9375rem, 메타 0.875rem. 0.875rem 미만·#6F6F6F 보다 옅은 회색 금지.
 * 콘텐츠 최대 폭 57.5rem.
 */

interface Props {
  card: AssistantCardCreate
  requesterName: string
  submitting: boolean
  submitted: boolean
  onCancel(): void
  onSubmit(): void
  onSupplement(field: string, answer: string): void
  supplementingField: string | null
  supplementedAnswers: Record<string, string>
  prevRisk: { grade: RiskGrade | null; score: number | null } | null
}

export default function CreateDoc({
  card,
  requesterName,
  submitting,
  submitted,
  onCancel,
  onSubmit,
  onSupplement,
  supplementingField,
  supplementedAnswers,
  prevRisk,
}: Props) {
  const { ticket, triage } = card
  const result = triage.result
  const headerText = label.docHeader(ticket.status)

  // 위험 요인 (점수 높은 순 정렬)
  type RiskRow = { factor: string; quote: string | null; points: number }
  const factorPoints = triage.factorPoints ?? null
  const riskRows: RiskRow[] = (result?.riskFactors ?? [])
    .map((f) => {
      const ev = result?.evidence?.find((e) => e.factor === f)
      return { factor: f, quote: ev?.quote ?? null, points: factorPoints?.[f] ?? 0 }
    })
    .sort((a, b) => b.points - a.points)

  const grade = triage.riskGrade
  const score = triage.riskScore
  const isHighRisk = grade === 'HIGH' || grade === 'CRITICAL'
  const changed = prevRisk && (prevRisk.grade !== grade || prevRisk.score !== score)

  // 질문 ↔ 필드 매칭. missingFields 와 clarifyingQuestions 는 같은 순서 (TriageValidator).
  const questions: Array<{ field: string; question: string }> = (result?.missingFields ?? []).map((field, i) => ({
    field,
    question: result?.clarifyingQuestions?.[i] ?? label.field(field),
  }))
  const remaining = questions.filter((q) => !supplementedAnswers[q.field]).length

  return (
    <article className="h-full overflow-y-auto">
      <div className="max-w-[57.5rem] px-16 pt-9 pb-0 min-h-full flex flex-col">
        {/* ① 상단 메타 */}
        <div className="flex justify-between items-baseline">
          <span className="text-[0.875rem] text-mute">{headerText}</span>
          <span className="text-[0.875rem] text-mute">
            <b className="text-ink font-semibold">{ticket.ticketNo}</b>
          </span>
        </div>

        {/* ② 제목 */}
        <h1 className="text-[2.125rem] leading-[1.3] font-bold tracking-[-0.015em] mt-4 mb-5">
          {documentTitle(result?.title, result?.category, result?.affectedService)}
        </h1>

        {/* ③ 핵심 요약 한 줄 */}
        <HeadlineSummary
          requestType={result?.requestType}
          grade={grade}
          score={score}
          isHighRisk={isHighRisk}
          changed={!!changed}
          prevGrade={prevRisk?.grade ?? null}
          prevScore={prevRisk?.score ?? null}
        />

        {/* ④ 보완 질문 */}
        {(questions.length > 0 || Object.keys(supplementedAnswers).length > 0) && (
          <Section
            title={remaining > 0 ? `접수 전에 ${remaining}가지 확인이 필요합니다` : '확인이 필요한 내용'}
            sub={remaining > 0 ? '아래 질문에 답해주시면 다시 분석합니다' : '모든 보완이 완료되었습니다'}
          >
            <div className="space-y-5">
              {/* 보완된 항목 (완료 블록) */}
              {Object.entries(supplementedAnswers)
                .filter(([f]) => !questions.some((q) => q.field === f))
                .map(([f, a]) => (
                  <SupplementedBlock key={f} field={f} answer={a} />
                ))}

              {questions.map(({ field, question }) => (
                <SupplementItem
                  key={field}
                  field={field}
                  question={question}
                  answer={supplementedAnswers[field] ?? null}
                  busy={supplementingField === field}
                  disabled={supplementingField !== null || submitted}
                  onSave={(ans) => onSupplement(field, ans)}
                />
              ))}
            </div>
          </Section>
        )}

        {/* ⑤ 위험 요인 */}
        {riskRows.length > 0 && (
          <Section title="위험 요인" sub="점수 높은 순">
            <div>
              {riskRows.map((r, i) => (
                <div
                  key={r.factor}
                  className="grid border-t border-line py-3 text-[1.0625rem] items-baseline"
                  style={{ gridTemplateColumns: '2.5rem 11.25rem 1fr 3.5rem' }}
                >
                  <span className="text-[0.875rem] text-mute">{String(i + 1).padStart(2, '0')}</span>
                  <span>{label.riskFactor(r.factor)}</span>
                  <span className="text-mute text-[0.9375rem]">{r.quote ? `"${r.quote}"` : ''}</span>
                  <span
                    className="text-right text-[1.0625rem] font-semibold"
                    style={r.points > 0 ? { color: 'var(--color-risk)' } : undefined}
                  >
                    {r.points > 0 ? `+${r.points}` : ''}
                  </span>
                </div>
              ))}
            </div>
          </Section>
        )}

        {/* ⑥ 요청 정보 */}
        <Section title="요청 정보">
          <div className="grid grid-cols-2 gap-x-14">
            <div>
              <Kv k="요청 유형" v={label.requestType(result?.requestType)} />
              <Kv k="분류" v={label.category(result?.category)} />
              <Kv k="환경" v={label.environment(result?.environment)} />
            </div>
            <div>
              <Kv k="대상 서비스" v={result?.affectedService ?? '—'} />
              <Kv k="작업 예정" v={formatRequestedTime(result?.requestedTime)} />
              <Kv k="요청자" v={requesterName} />
            </div>
          </div>
        </Section>

        {/* ⑦ 자동 보정 (접힘) */}
        {result?.corrections && result.corrections.length > 0 && (
          <CollapsibleCorrections corrections={result.corrections} />
        )}

        {/* ⑧ 푸터 */}
        <footer className="mt-auto border-t border-line flex justify-between items-center pt-5 pb-6">
          <span className="text-[0.875rem] text-mute max-w-[28.75rem]">
            {submitted
              ? '접수가 완료되어 백오피스에 등록되었습니다.'
              : '접수하기 전에는 백오피스에 등록되지 않습니다.'}
          </span>
          <div className="flex gap-2 items-center">
            {!submitted && (
              <button
                type="button"
                onClick={onCancel}
                disabled={submitting}
                className="h-11 px-3 text-[0.9375rem] underline underline-offset-4 disabled:opacity-40"
              >
                취소
              </button>
            )}
            <button
              type="button"
              onClick={onSubmit}
              disabled={submitting || submitted}
              className="h-11 px-6 bg-accent hover:bg-accent-hover transition-colors text-white text-[0.9375rem] font-semibold disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {submitted ? '접수됨' : submitting ? '접수 중…' : '접수하기'}
            </button>
          </div>
        </footer>
      </div>
    </article>
  )
}

function HeadlineSummary({
  requestType,
  grade,
  score,
  isHighRisk,
  changed,
  prevGrade,
  prevScore,
}: {
  requestType?: string | null
  grade: RiskGrade | null
  score: number | null
  isHighRisk: boolean
  changed: boolean
  prevGrade: RiskGrade | null
  prevScore: number | null
}) {
  const typeLabel = label.requestType(requestType)
  const needsApproval = requestType === 'CHANGE' && isHighRisk
  const prefix = needsApproval ? '승인이 필요한 변경' : typeLabel

  const riskPart =
    grade && score != null
      ? ` · 위험도 ${label.riskGrade(grade)} (${score}점)`
      : ''

  return (
    <div
      className="mb-8 text-[1.375rem] font-bold leading-[1.4]"
      style={isHighRisk ? { color: 'var(--color-risk)' } : undefined}
    >
      {prefix}
      {riskPart}
      {changed && prevGrade && prevScore != null && grade && score != null && (
        <div className="mt-1 text-[0.9375rem] font-normal text-mute">
          {label.riskGrade(prevGrade)} {prevScore}점 → {label.riskGrade(grade)} {score}점
        </div>
      )}
    </div>
  )
}

function CollapsibleCorrections({ corrections }: { corrections: string[] }) {
  const [open, setOpen] = useState(false)
  return (
    <section className="mt-9 border-t border-line pt-4">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="text-[0.9375rem] text-mute underline underline-offset-4 hover:text-ink transition-colors"
      >
        자동 보정 {corrections.length}건 · {open ? '접기' : '자세히 보기'}
      </button>
      {open && (
        <div className="mt-3 text-[0.9375rem] text-mute leading-[1.75]">
          {corrections.map((c, i) => (
            <div key={i}>— {formatCorrection(c)}</div>
          ))}
          <div className="mt-2 text-[0.875rem]">보정이 있었던 요청은 담당자가 한 번 더 검토합니다.</div>
        </div>
      )}
    </section>
  )
}

function SupplementItem({
  field,
  question,
  answer,
  busy,
  disabled,
  onSave,
}: {
  field: string
  question: string
  answer: string | null
  busy: boolean
  disabled: boolean
  onSave(ans: string): void
}) {
  const [draft, setDraft] = useState('')

  if (answer) {
    return <SupplementedBlock field={field} answer={answer} />
  }

  function save() {
    const t = draft.trim()
    if (!t || busy) return
    onSave(t)
    setDraft('')
  }

  return (
    <div className="border-t border-line pt-3">
      <p className="text-[1.0625rem]">{question}</p>
      <textarea
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        placeholder={`예) ${placeholderFor(field)}`}
        rows={2}
        disabled={disabled || busy}
        className="w-full mt-3 py-2 text-[1.0625rem] resize-none bg-transparent focus:outline-none placeholder:text-mute disabled:opacity-50"
        style={{ borderBottom: '0.0625rem solid var(--color-ink)' }}
      />
      <div className="mt-3 flex items-center gap-3">
        <button
          type="button"
          onClick={save}
          disabled={disabled || busy || !draft.trim()}
          className="h-10 px-4 bg-accent hover:bg-accent-hover transition-colors text-white text-[0.9375rem] font-semibold disabled:opacity-40 disabled:cursor-not-allowed"
        >
          {busy ? '다시 분석하고 있습니다…' : '답변 저장'}
        </button>
        {busy && <span className="text-[0.875rem] text-mute">잠시만 기다려 주세요</span>}
      </div>
    </div>
  )
}

function SupplementedBlock({ field, answer }: { field: string; answer: string }) {
  return (
    <div className="border-t border-line pt-3">
      <p className="text-[0.9375rem] text-mute">{label.field(field)}</p>
      <p className="text-[1.0625rem] mt-1">
        <span className="text-mute mr-1.5">보완 완료 ·</span>
        {answer}
      </p>
    </div>
  )
}

function Kv({ k, v }: { k: string; v: string }) {
  return (
    <div
      className="grid border-t border-line py-3 text-[1.0625rem]"
      style={{ gridTemplateColumns: '7.5rem 1fr' }}
    >
      <span className="text-[0.9375rem] text-mute">{k}</span>
      <span>{v}</span>
    </div>
  )
}

function Section({
  title,
  sub,
  children,
}: {
  title: string
  sub?: string
  children: React.ReactNode
}) {
  return (
    <section className="mt-9">
      <h3 className="text-[0.9375rem] font-semibold mb-3">
        {title}
        {sub && (
          <small className="block font-normal text-mute text-[0.875rem] mt-0.5">{sub}</small>
        )}
      </h3>
      {children}
    </section>
  )
}

function documentTitle(
  aiTitle: string | undefined | null,
  category: string | undefined | null,
  service: string | null | undefined,
): string {
  // Validator 가 항상 title 을 채워주지만, 혹시 비어 있거나 과거 레코드면 폴백.
  const t = aiTitle?.trim()
  if (t) return t
  const cat = label.category(category)
  const svc = service?.trim()
  if (svc && cat !== '—') return `${svc} ${cat}`
  if (svc) return svc
  if (cat !== '—') return cat
  return '요청서'
}

function placeholderFor(key: string): string {
  const examples: Record<string, string> = {
    rollbackPlan: '추가한 컬럼 삭제 스크립트, 작업 전 테이블 백업',
    testPlan: '스테이징에서 동일 쿼리 검증, 영향 사용자 수 확인',
    scheduledTime: '오늘 23:00',
    sourceIp: '10.0.1.23',
    destinationIp: '10.0.2.11',
    port: '3306',
    protocol: 'TCP',
    reason: '월말 정산 데이터 집계용',
    errorMessage: '"접속 시 500 오류"',
    affectedService: '학생 포털',
    environment: '운영',
  }
  return examples[key] ?? '간단히 설명해 주세요'
}
