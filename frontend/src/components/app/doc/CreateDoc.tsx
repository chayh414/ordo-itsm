import type { AssistantCardCreate } from '../../../lib/types'
import { formatRequestedTime, label } from '../../../lib/i18n'

/*
 * create_request 결과 요청서. 디자인 가이드 v3 §4 에 명시된 섹션 순서:
 * 메타(상태·요청번호) → 제목 → 2단 kv표 → 위험도 → 확인 필요 → 자동 보정 → 하단 안내+버튼
 *
 * 위험 점수·등급은 백엔드가 아직 안 주므로 자리만 (데이터 있으면 자동 표시).
 */

interface Props {
  card: AssistantCardCreate
  requesterName: string
  submitting: boolean
  submitted: boolean
  onCancel(): void
  onSubmit(): void
}

export default function CreateDoc({ card, requesterName, submitting, submitted, onCancel, onSubmit }: Props) {
  const { ticket, triage } = card
  const result = triage.result
  const displayStatus = submitted ? ticket.status : ticket.status // ticket.status 는 호출자가 submit 성공 후 갱신해줌
  const headerText = label.docHeader(displayStatus)

  // 위험 요인 행: evidence 가 있는 것은 quote 같이, 없으면 라벨만. 점수는 factorPoints 에서.
  type RiskRow = { factor: string; quote: string | null; points: number | null }
  const factorPoints = triage.factorPoints ?? null
  const riskRows: RiskRow[] = (result?.riskFactors ?? []).map((f) => {
    const ev = result?.evidence?.find((e) => e.factor === f)
    return {
      factor: f,
      quote: ev?.quote ?? null,
      points: factorPoints ? factorPoints[f] ?? null : null,
    }
  })

  // 위험 점수·등급 — CHANGE 요청만 백엔드가 돌려줌. 그 외는 null (섹션 extra 숨김).
  const grade = triage.riskGrade
  const score = triage.riskScore
  const isHighRisk = grade === 'HIGH' || grade === 'CRITICAL'

  const firstQuestion = result?.clarifyingQuestions?.[0] ?? null
  const firstMissing = result?.missingFields?.[0]
  const placeholder = firstMissing
    ? `예) ${placeholderFor(firstMissing)}`
    : '여기에 추가 설명을 적어주세요'

  return (
    <article className="h-full flex flex-col overflow-y-auto px-16 pt-9 pb-0">
      {/* 메타 */}
      <div className="flex justify-between items-baseline">
        <span className="text-[13px] text-mute">{headerText}</span>
        <span className="text-[13px] text-mute">
          <b className="text-ink font-semibold">{ticket.ticketNo}</b>
        </span>
      </div>

      {/* 제목 */}
      <h1 className="text-[34px] leading-[1.3] font-bold tracking-[-0.015em] mt-4 mb-7 max-w-[640px]">
        {ticket.sla == null && ticket.triage == null
          ? documentTitle(result?.category, result?.affectedService)
          : documentTitle(result?.category, result?.affectedService)}
      </h1>

      {/* 2단 kv */}
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

      {/* 위험도 */}
      <Section
        title="위험도"
        sub="규칙에 따라 계산"
        extra={
          grade && score != null ? (
            <>
              <div
                className="mt-1.5 text-[15px] font-semibold"
                style={isHighRisk ? { color: 'var(--color-risk)' } : undefined}
              >
                {label.riskGrade(grade)} · {score}점
              </div>
              {isHighRisk && (
                <div className="text-[13px] text-mute mt-1">승인이 필요한 작업</div>
              )}
            </>
          ) : null
        }
      >
        {riskRows.length === 0 ? (
          <p className="text-[14px] text-mute pt-3">식별된 위험 요인이 없습니다.</p>
        ) : (
          <div>
            {riskRows.map((r, i) => (
              <div
                key={r.factor}
                className="grid border-t border-line py-2.5 text-[15px] items-baseline"
                style={{ gridTemplateColumns: '36px 150px 1fr 40px' }}
              >
                <span className="text-[13px] text-faint">{String(i + 1).padStart(2, '0')}</span>
                <span>{label.riskFactor(r.factor)}</span>
                <span className="text-mute">{r.quote ? `"${r.quote}"` : ''}</span>
                <span
                  className="text-right text-[15px] font-semibold"
                  style={r.points != null ? { color: 'var(--color-risk)' } : undefined}
                >
                  {r.points != null ? `+${r.points}` : ''}
                </span>
              </div>
            ))}
          </div>
        )}
      </Section>

      {/* 확인이 필요한 내용 */}
      {firstQuestion && (
        <Section title="확인이 필요한 내용">
          <div className="border-t border-line pt-3">
            <p className="text-[15px]">{firstQuestion}</p>
            <div
              className="mt-2.5 pt-1.5 pb-1.5 text-[15px] text-faint max-w-[520px]"
              style={{ borderBottom: '1px solid var(--color-ink)' }}
            >
              {placeholder}
            </div>
          </div>
        </Section>
      )}

      {/* 자동 보정 기록 */}
      {result?.corrections && result.corrections.length > 0 && (
        <Section title="자동 보정 기록">
          <div className="border-t border-line pt-2.5 text-[13px] text-mute leading-[1.7]">
            {result.corrections.map((c, i) => (
              <div key={i}>{c}</div>
            ))}
            <div className="mt-1">보정이 있었던 요청은 담당자가 한 번 더 검토합니다.</div>
          </div>
        </Section>
      )}

      {/* 푸터 */}
      <footer className="mt-auto border-t border-line flex justify-between items-center pt-4 pb-5">
        <span className="text-[13px] text-mute max-w-[420px]">
          {submitted
            ? '접수가 완료되어 백오피스에 등록되었습니다.'
            : '접수하기 전에는 백오피스에 등록되지 않습니다.'}
        </span>
        <div className="flex gap-1.5 items-center">
          {!submitted && (
            <button
              type="button"
              onClick={onCancel}
              disabled={submitting}
              className="h-11 px-3 text-[15px] underline underline-offset-4 disabled:opacity-40"
            >
              취소
            </button>
          )}
          <button
            type="button"
            onClick={onSubmit}
            disabled={submitting || submitted}
            className="h-11 px-6 bg-ink text-white text-[15px] font-semibold disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {submitted ? '접수됨' : submitting ? '접수 중…' : '접수하기'}
          </button>
        </div>
      </footer>
    </article>
  )
}

function Kv({ k, v }: { k: string; v: string }) {
  return (
    <div
      className="grid border-t border-line py-2.5 text-[15px]"
      style={{ gridTemplateColumns: '110px 1fr' }}
    >
      <span className="text-mute">{k}</span>
      <span>{v}</span>
    </div>
  )
}

function Section({
  title,
  sub,
  extra,
  children,
}: {
  title: string
  sub?: string
  extra?: React.ReactNode
  children: React.ReactNode
}) {
  return (
    <section
      className="mt-7 grid gap-x-6"
      style={{ gridTemplateColumns: '180px 1fr' }}
    >
      <div>
        <h3 className="text-[15px] font-semibold">
          {title}
          {sub && (
            <small className="block font-normal text-mute text-[13px] mt-0.5">{sub}</small>
          )}
        </h3>
        {extra}
      </div>
      <div>{children}</div>
    </section>
  )
}

function documentTitle(category: string | undefined | null, service: string | null | undefined): string {
  const cat = label.category(category)
  const svc = service?.trim()
  if (svc && cat !== '—') return `${svc} ${cat}`
  if (svc) return svc
  if (cat !== '—') return cat
  return '요청서'
}

/** missing field 키 → 입력 힌트 placeholder (디자인 가이드의 "예)" 예시). */
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
