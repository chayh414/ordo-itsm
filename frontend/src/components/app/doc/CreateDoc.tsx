import { useState } from 'react'
import type { AssistantCardCreate, RiskGrade } from '../../../lib/types'
import { formatCorrection, formatRequestedTime, label } from '../../../lib/i18n'

/*
 * create_request 결과 요청서 (v4 — 2열 사이드바 · 번호화된 보완 질문 · 접수 완료 화면).
 *
 * xl(1280px+) 레이아웃:
 *   왼쪽: 제목 · 보완 질문 · 요청 정보 · 자동 보정
 *   오른쪽 sticky 사이드바: 위험도 · 위험 요인(합계 = 점수) · 보완 진행 · [접수][취소] · 안내문
 *
 * xl 미만: 1열. 위험 요인·접수 버튼은 하단에.
 * 접수 후: 오른쪽 패널을 "접수 완료" 로 전환.
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
  /** 접수 완료 화면의 [내 요청 보기] — 대화창에 "내 요청 보여줘" 전송 */
  onSendChat?: (text: string) => void
  /** 접수 완료 화면의 [새 요청 작성] — 패널·대화창 초기화 */
  onNewRequest?: () => void
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
  onSendChat,
  onNewRequest,
}: Props) {
  const { ticket, triage } = card
  const result = triage.result
  const category = result?.category ?? null

  // 위험 요인 — factorPoints 기준으로 그려 "목록 합계 = 헤더 점수" 항상 성립
  type RiskRow = { factor: string; quote: string | null; points: number }
  const factorPoints = triage.factorPoints ?? {}
  const quoteByFactor = new Map<string, string>()
  for (const e of result?.evidence ?? []) {
    if (e.quote) quoteByFactor.set(e.factor, e.quote)
  }
  const riskRows: RiskRow[] = Object.entries(factorPoints)
    .map(([factor, points]) => ({
      factor,
      points: typeof points === 'number' ? points : 0,
      quote: quoteByFactor.get(factor) ?? null,
    }))
    .sort((a, b) => b.points - a.points)
  const riskSum = riskRows.reduce((a, b) => a + b.points, 0)

  const grade = triage.riskGrade
  const score = triage.riskScore

  // 보완 질문 — missingFields·clarifyingQuestions 는 같은 순서 (TriageValidator)
  const openQuestions = (result?.missingFields ?? [])
    .filter((f) => !supplementedAnswers[f])
    .map((field, i) => ({
      field,
      question: result?.clarifyingQuestions?.[i] ?? label.field(field),
    }))
  const totalQ = openQuestions.length + Object.keys(supplementedAnswers).length
  const completedQ = Object.keys(supplementedAnswers).length
  const allDone = openQuestions.length === 0 && totalQ > 0
  const supplementHeaderText =
    totalQ === 0
      ? '확인이 필요한 내용이 없습니다'
      : allDone
        ? `접수 전 확인 필요 · 전체 완료 (${completedQ} / ${totalQ})`
        : `접수 전 확인 필요 · ${completedQ} / ${totalQ}`

  // 접수 후 화면
  if (submitted) {
    return (
      <SubmittedState
        ticket={ticket}
        statusLabel={label.status(ticket.status)}
        score={score}
        grade={grade}
        openQuestions={openQuestions}
        onSendChat={onSendChat}
        onNewRequest={onNewRequest}
      />
    )
  }

  const header = (
    <>
      <div className="flex justify-between items-baseline">
        <span className="text-[0.875rem] text-mute">{label.docHeader(ticket.status)}</span>
        <span className="text-[0.875rem] text-mute">
          <b className="text-ink font-semibold">{ticket.ticketNo}</b>
        </span>
      </div>
      <h1 className="text-[2.125rem] leading-[1.3] font-bold tracking-[-0.015em] mt-4 mb-7">
        {documentTitle(result?.title, result?.category, result?.affectedService)}
      </h1>
    </>
  )

  const supplementsBlock = (totalQ > 0) && (
    <section className="mt-2">
      <h3 className="text-[0.9375rem] font-semibold mb-4">{supplementHeaderText}</h3>
      <div className="divide-y divide-line border-t border-b border-line">
        {Object.entries(supplementedAnswers).map(([f, a], idx) => (
          <AnsweredRow
            key={f}
            number={rowNumber(idx)}
            field={f}
            answer={a}
          />
        ))}
        {openQuestions.map((q, i) => (
          <OpenRow
            key={q.field}
            number={rowNumber(completedQ + i)}
            field={q.field}
            question={q.question}
            category={category}
            busy={supplementingField === q.field}
            disabled={supplementingField !== null}
            onSave={(ans) => onSupplement(q.field, ans)}
          />
        ))}
      </div>
    </section>
  )

  const requestInfoBlock = (
    <section className="mt-10">
      <h3 className="text-[0.9375rem] font-semibold mb-3">요청 정보</h3>
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
    </section>
  )

  const correctionsBlock = result?.corrections && result.corrections.length > 0
    ? <CollapsibleCorrections corrections={result.corrections} />
    : null

  return (
    <article className="h-full overflow-y-auto">
      <div className="min-h-full xl:grid xl:gap-10 pt-9 pb-6 px-10"
           style={{ gridTemplateColumns: '1fr 20rem' }}>
        {/* 왼쪽: 메인 콘텐츠 */}
        <div className="max-w-[52rem]">
          {header}
          {supplementsBlock}
          {requestInfoBlock}
          {correctionsBlock}

          {/* xl 미만: 하단 위험 요인 + 접수 버튼 */}
          <div className="xl:hidden mt-10">
            <RiskRowsList rows={riskRows} />
            <BottomBar
              submitting={submitting}
              onCancel={onCancel}
              onSubmit={onSubmit}
            />
          </div>
        </div>

        {/* 오른쪽: sticky 사이드바 (xl 이상) */}
        <aside className="hidden xl:block">
          <div className="sticky top-6 border border-line p-6">
            <RiskSummary grade={grade} score={score} prevRisk={prevRisk} />
            <RiskRowsList rows={riskRows} compact />
            {totalQ > 0 && (
              <div className="mt-6 pt-5 border-t border-line text-[0.875rem] text-mute">
                보완 진행 · <b className="text-ink font-semibold">{completedQ} / {totalQ}</b>
              </div>
            )}
            <SidebarButtons
              submitting={submitting}
              onCancel={onCancel}
              onSubmit={onSubmit}
            />
            <p className="mt-4 text-[0.75rem] text-mute leading-[1.55]">
              접수하기 전에는 백오피스에 등록되지 않습니다.
            </p>
          </div>
        </aside>
      </div>
    </article>
  )
}

/* ------------------------------------------------------------------ */
/*  보완 질문                                                           */
/* ------------------------------------------------------------------ */

function rowNumber(i: number) {
  return String(i + 1).padStart(2, '0')
}

function OpenRow({
  number, field, question, category, busy, disabled, onSave,
}: {
  number: string
  field: string
  question: string
  category: string | null
  busy: boolean
  disabled: boolean
  onSave(ans: string): void
}) {
  const [draft, setDraft] = useState('')
  const canSave = draft.trim().length > 0 && !busy && !disabled
  const hint = hintFor(field)

  function save() {
    if (!canSave) return
    onSave(draft.trim())
    setDraft('')
  }

  return (
    <div className="py-4">
      <div className="flex items-baseline gap-3">
        <span className="text-[0.8125rem] text-mute font-feature-settings-[tnum] tabular-nums">{number}</span>
        <strong className="text-[1rem] font-semibold">{label.field(field)}</strong>
      </div>
      {hint && <p className="ml-9 mt-1 text-[0.8125rem] text-mute">{hint}</p>}
      {question && question !== label.field(field) && (
        <p className="ml-9 mt-1 text-[0.8125rem] text-mute">{question}</p>
      )}
      <div className="ml-9 mt-2 flex items-stretch gap-2">
        <input
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={`예) ${placeholderFor(field, category)}`}
          disabled={disabled || busy}
          onKeyDown={(e) => {
            if (e.key === 'Enter') { e.preventDefault(); save() }
          }}
          className="flex-1 border border-line px-3 py-2 text-[0.9375rem] bg-transparent focus:border-ink focus:outline-none placeholder:text-faint disabled:opacity-50"
        />
        <button
          type="button"
          onClick={save}
          disabled={!canSave}
          className={[
            'h-10 px-4 text-[0.875rem] font-semibold transition-colors',
            canSave
              ? 'bg-accent hover:bg-accent-hover text-white'
              : 'bg-white border border-line text-mute cursor-not-allowed',
          ].join(' ')}
        >
          {busy ? '분석 중…' : '저장'}
        </button>
      </div>
    </div>
  )
}

function AnsweredRow({
  number, field, answer,
}: { number: string; field: string; answer: string }) {
  const [expanded, setExpanded] = useState(false)
  return (
    <div className="py-3">
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="w-full text-left grid items-baseline gap-3 hover:bg-soft/50"
        style={{ gridTemplateColumns: '2rem auto auto 1fr' }}
      >
        <span className="text-[0.8125rem] text-mute tabular-nums">{number}</span>
        <strong className="text-[0.9375rem] font-semibold">{label.field(field)}</strong>
        <span className="text-[0.8125rem] text-mute">✓ 저장됨</span>
        <span className="text-[0.9375rem] text-ink truncate">{answer}</span>
      </button>
      {expanded && (
        <p className="ml-9 mt-2 text-[0.8125rem] text-mute">
          수정하려면 질문 전체를 다시 입력해주세요 (대화창에서 요청하면 재분석됩니다).
        </p>
      )}
    </div>
  )
}

/* ------------------------------------------------------------------ */
/*  위험 요인                                                           */
/* ------------------------------------------------------------------ */

function RiskRowsList({
  rows, compact = false,
}: { rows: { factor: string; quote: string | null; points: number }[]; compact?: boolean }) {
  if (rows.length === 0) return null
  const sum = rows.reduce((a, b) => a + b.points, 0)
  return (
    <section className={compact ? 'mt-5' : 'mt-2'}>
      {!compact && <h3 className="text-[0.9375rem] font-semibold mb-3">위험 요인</h3>}
      <div className="border-t border-line">
        {rows.map((r) => (
          <div
            key={r.factor}
            className={`grid border-b border-line ${compact ? 'py-2' : 'py-3'} text-[0.9375rem] items-baseline`}
            style={{ gridTemplateColumns: '1fr auto' }}
          >
            <span>{label.riskFactor(r.factor)}</span>
            <span className="font-semibold" style={{ color: 'var(--color-risk)' }}>
              +{r.points}
            </span>
          </div>
        ))}
        <div className="grid py-3 text-[0.9375rem] items-baseline"
             style={{ gridTemplateColumns: '1fr auto' }}>
          <span className="text-mute">합계</span>
          <b className="font-bold text-[1rem]" style={{ color: 'var(--color-risk)' }}>{sum}</b>
        </div>
      </div>
    </section>
  )
}

/* ------------------------------------------------------------------ */
/*  사이드바                                                            */
/* ------------------------------------------------------------------ */

function RiskSummary({
  grade, score, prevRisk,
}: {
  grade: RiskGrade | null
  score: number | null
  prevRisk: { grade: RiskGrade | null; score: number | null } | null
}) {
  const changed = prevRisk && (prevRisk.grade !== grade || prevRisk.score !== score)
  return (
    <div>
      <p className="text-[0.75rem] text-mute">위험도</p>
      <div className="mt-2 flex items-baseline gap-2">
        <b className="text-[2rem] font-bold leading-none" style={{ color: 'var(--color-risk)' }}>
          {score ?? '—'}
        </b>
        <span className="text-[1rem] text-mute">점</span>
        {grade && (
          <span className="ml-auto text-[0.9375rem] font-semibold" style={{ color: 'var(--color-risk)' }}>
            {label.riskGrade(grade)}
          </span>
        )}
      </div>
      {changed && prevRisk?.score != null && (
        <p className="mt-1 text-[0.75rem] text-mute">이전 {prevRisk.score}점 → 지금 {score}점</p>
      )}
    </div>
  )
}

function SidebarButtons({
  submitting, onCancel, onSubmit,
}: { submitting: boolean; onCancel(): void; onSubmit(): void }) {
  return (
    <div className="mt-6 pt-5 border-t border-line flex flex-col gap-2">
      <button
        type="button"
        onClick={onSubmit}
        disabled={submitting}
        className="h-11 bg-accent hover:bg-accent-hover transition-colors text-white text-[0.9375rem] font-semibold disabled:opacity-40 disabled:cursor-not-allowed"
      >
        {submitting ? '접수 중…' : '접수하기'}
      </button>
      <button
        type="button"
        onClick={onCancel}
        disabled={submitting}
        className="h-10 text-[0.875rem] underline underline-offset-4 disabled:opacity-40"
      >
        취소
      </button>
    </div>
  )
}

function BottomBar({
  submitting, onCancel, onSubmit,
}: { submitting: boolean; onCancel(): void; onSubmit(): void }) {
  return (
    <footer className="mt-10 border-t border-line flex justify-between items-center pt-5 pb-6">
      <span className="text-[0.875rem] text-mute max-w-[28.75rem]">
        접수하기 전에는 백오피스에 등록되지 않습니다.
      </span>
      <div className="flex gap-2 items-center">
        <button
          type="button"
          onClick={onCancel}
          disabled={submitting}
          className="h-11 px-3 text-[0.9375rem] underline underline-offset-4 disabled:opacity-40"
        >
          취소
        </button>
        <button
          type="button"
          onClick={onSubmit}
          disabled={submitting}
          className="h-11 px-6 bg-accent hover:bg-accent-hover transition-colors text-white text-[0.9375rem] font-semibold disabled:opacity-40 disabled:cursor-not-allowed"
        >
          {submitting ? '접수 중…' : '접수하기'}
        </button>
      </div>
    </footer>
  )
}

/* ------------------------------------------------------------------ */
/*  접수 완료 화면                                                       */
/* ------------------------------------------------------------------ */

function SubmittedState({
  ticket, statusLabel, score, grade, openQuestions, onSendChat, onNewRequest,
}: {
  ticket: AssistantCardCreate['ticket']
  statusLabel: string
  score: number | null
  grade: RiskGrade | null
  openQuestions: Array<{ field: string; question: string }>
  onSendChat?: (text: string) => void
  onNewRequest?: () => void
}) {
  const sla = (ticket as unknown as { sla?: { resolutionDueAt?: string | null } }).sla
  const resolutionDueAt = sla?.resolutionDueAt ?? null
  const submittedAt =
    (ticket as unknown as { submittedAt?: string | null }).submittedAt
    ?? (ticket as unknown as { createdAt?: string | null }).createdAt
    ?? null

  return (
    <article className="h-full overflow-y-auto">
      <div className="min-h-full px-12 pt-16 pb-8 max-w-[40rem] mx-auto">
        <div className="text-[0.875rem] text-mute">접수 완료</div>
        <h1 className="mt-3 text-[2rem] font-bold tracking-[-0.015em]">
          요청이 접수되었습니다
        </h1>
        <p className="mt-3 text-[0.9375rem] text-mute leading-[1.6]">
          백오피스에 등록되었고 지금부터 SLA 가 시작됩니다.
          <br />
          처리 현황은 대화창에서 "내 요청 보여줘" 로 확인할 수 있어요.
        </p>

        <dl className="mt-9 border-t border-line">
          <RowKV k="요청 번호" v={ticket.ticketNo} />
          <RowKV k="상태" v={statusLabel} />
          <RowKV k="접수 시각" v={formatDateTime(submittedAt)} />
          <RowKV k="SLA 마감" v={formatDateTime(resolutionDueAt)} />
          {score != null && grade != null && (
            <RowKV k="위험도" v={`${label.riskGrade(grade)} · ${score}점`} valueColor="risk" />
          )}
          {openQuestions.length > 0 && (
            <RowKV
              k="남은 보완"
              v={openQuestions.map((q) => label.field(q.field)).join(', ')}
            />
          )}
        </dl>

        <div className="mt-10 flex items-center gap-3">
          <button
            type="button"
            onClick={() => onSendChat?.('내 요청 보여줘')}
            disabled={!onSendChat}
            className="h-11 px-5 bg-accent hover:bg-accent-hover transition-colors text-white text-[0.9375rem] font-semibold disabled:opacity-40"
          >
            내 요청 보기
          </button>
          <button
            type="button"
            onClick={() => onNewRequest?.()}
            disabled={!onNewRequest}
            className="h-11 px-5 border border-line text-[0.9375rem] disabled:opacity-40"
          >
            새 요청 작성
          </button>
        </div>
      </div>
    </article>
  )
}

function RowKV({
  k, v, valueColor,
}: { k: string; v: string; valueColor?: 'risk' }) {
  return (
    <div className="grid border-b border-line py-3 text-[1rem]"
         style={{ gridTemplateColumns: '9rem 1fr' }}>
      <span className="text-[0.875rem] text-mute">{k}</span>
      <span style={valueColor === 'risk' ? { color: 'var(--color-risk)' } : undefined}
            className={valueColor === 'risk' ? 'font-semibold' : undefined}>
        {v}
      </span>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/*  보조                                                               */
/* ------------------------------------------------------------------ */

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

function Kv({ k, v }: { k: string; v: string }) {
  return (
    <div
      className="grid border-t border-line py-3 text-[1rem]"
      style={{ gridTemplateColumns: '7.5rem 1fr' }}
    >
      <span className="text-[0.875rem] text-mute">{k}</span>
      <span>{v}</span>
    </div>
  )
}

function documentTitle(
  aiTitle: string | undefined | null,
  category: string | undefined | null,
  service: string | null | undefined,
): string {
  const t = aiTitle?.trim()
  if (t) return t
  const cat = label.category(category)
  const svc = service?.trim()
  if (svc && cat !== '—') return `${svc} ${cat}`
  if (svc) return svc
  if (cat !== '—') return cat
  return '요청서'
}

function placeholderFor(key: string, category?: string | null): string {
  if (key === 'rollbackPlan') {
    switch (category) {
      case 'FIREWALL': return '추가한 허용 규칙 즉시 삭제, 작업 전 설정 백업'
      case 'NETWORK': return '변경한 라우팅 즉시 원복, 작업 전 설정 백업'
      case 'DATABASE_SCHEMA': return '추가한 컬럼 삭제 스크립트, 작업 전 테이블 백업'
      case 'DATABASE_PERMISSION': return '부여한 권한 즉시 회수'
      case 'SERVER': return '변경 전 설정 백업 후 원복'
      default: return '변경 전 상태로 되돌릴 절차를 적어주세요'
    }
  }
  if (key === 'testPlan') {
    switch (category) {
      case 'FIREWALL':
      case 'NETWORK': return '스테이징에서 연결 테스트 완료'
      case 'DATABASE_SCHEMA':
      case 'DATABASE_PERMISSION': return '스테이징에서 동일 쿼리 검증, 영향 사용자 수 확인'
      default: return '테스트 환경에서 검증 완료'
    }
  }
  const examples: Record<string, string> = {
    scheduledTime: '2026-10-16 23:00',
    sourceIp: '10.0.1.23',
    destinationIp: '10.0.2.11',
    port: '3306',
    protocol: 'TCP',
    reason: '월말 정산 데이터 집계용',
    errorMessage: '"접속 시 500 오류"',
    affectedService: '주문 시스템',
    environment: '운영',
  }
  return examples[key] ?? '간단히 설명해 주세요'
}

function hintFor(field: string): string | null {
  // 짧은 보조 — 필요한 필드만.
  const hints: Record<string, string> = {
    sourceIp: '예: 10.0.x.x',
    destinationIp: '예: 10.0.x.x',
    scheduledTime: '날짜 + 시각',
    port: '숫자만',
  }
  return hints[field] ?? null
}

function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  try {
    const d = new Date(iso)
    const Y = d.getFullYear()
    const M = String(d.getMonth() + 1).padStart(2, '0')
    const D = String(d.getDate()).padStart(2, '0')
    const h = String(d.getHours()).padStart(2, '0')
    const m = String(d.getMinutes()).padStart(2, '0')
    return `${Y}-${M}-${D} ${h}:${m}`
  } catch {
    return iso
  }
}
