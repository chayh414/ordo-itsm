import type { AssistantCardGet } from '../../../lib/types'
import { formatWhen, label } from '../../../lib/i18n'

/*
 * get_request 상세. 읽기 전용 요청서. CreateDoc 과 동일한 섹션 순서·크기 규칙.
 * 글자: 본문 17px, 라벨 15px, 메타 14px (14px 미만 금지, 회색 최소 #6F6F6F).
 */

interface Props {
  card: AssistantCardGet
}

export default function GetDoc({ card }: Props) {
  const { ticket, triage } = card
  const result = triage?.result ?? null

  const factorPoints = triage?.factorPoints ?? null
  const riskRows = (result?.riskFactors ?? [])
    .map((f) => {
      const ev = result?.evidence?.find((e) => e.factor === f)
      return {
        factor: f,
        quote: ev?.quote ?? null,
        points: factorPoints?.[f] ?? 0,
      }
    })
    .sort((a, b) => b.points - a.points)
  const grade = triage?.riskGrade ?? null
  const score = triage?.riskScore ?? null
  const isHighRisk = grade === 'HIGH' || grade === 'CRITICAL'
  const requestType = ticket.requestType
  const needsApproval = requestType === 'CHANGE' && isHighRisk
  const headlinePrefix = needsApproval ? '승인이 필요한 변경' : label.requestType(requestType)
  const headlineRisk =
    grade && score != null ? ` · 위험도 ${label.riskGrade(grade)} (${score}점)` : ''

  return (
    <article className="h-full overflow-y-auto">
      <div className="mx-auto max-w-[920px] px-16 pt-9 pb-10">
        <div className="flex justify-between items-baseline">
          <span className="text-[14px] text-mute">{label.docHeader(ticket.status)}</span>
          <span className="text-[14px] text-mute">
            <b className="text-ink font-semibold">{ticket.ticketNo}</b>
          </span>
        </div>

        <h1 className="text-[34px] leading-[1.3] font-bold tracking-[-0.015em] mt-4 mb-5">
          {ticket.subject}
        </h1>

        {/* 핵심 요약 */}
        {(grade || requestType) && (
          <div
            className="mb-8 text-[22px] font-bold leading-[1.4]"
            style={isHighRisk ? { color: 'var(--color-risk)' } : undefined}
          >
            {headlinePrefix}
            {headlineRisk}
          </div>
        )}

        {riskRows.length > 0 && (
          <section className="mt-7">
            <h3 className="text-[15px] font-semibold mb-3">
              위험 요인
              <small className="block font-normal text-mute text-[14px] mt-0.5">점수 높은 순</small>
            </h3>
            <div>
              {riskRows.map((r, i) => (
                <div
                  key={r.factor}
                  className="grid border-t border-line py-3 text-[17px] items-baseline"
                  style={{ gridTemplateColumns: '40px 180px 1fr 56px' }}
                >
                  <span className="text-[14px] text-mute">{String(i + 1).padStart(2, '0')}</span>
                  <span>{label.riskFactor(r.factor)}</span>
                  <span className="text-mute text-[15px]">{r.quote ? `"${r.quote}"` : ''}</span>
                  <span
                    className="text-right text-[17px] font-semibold"
                    style={r.points > 0 ? { color: 'var(--color-risk)' } : undefined}
                  >
                    {r.points > 0 ? `+${r.points}` : ''}
                  </span>
                </div>
              ))}
            </div>
          </section>
        )}

        <section className="mt-9">
          <h3 className="text-[15px] font-semibold mb-3">요청 정보</h3>
          <div className="grid grid-cols-2 gap-x-14">
            <div>
              <Kv k="요청 유형" v={label.requestType(ticket.requestType)} />
              <Kv k="분류" v={label.category(ticket.category)} />
              <Kv k="환경" v={label.environment(ticket.environment)} />
            </div>
            <div>
              <Kv k="대상 서비스" v={ticket.affectedService ?? '—'} />
              <Kv
                k="SLA 마감"
                v={ticket.sla?.resolutionDueAt ? formatWhen(ticket.sla.resolutionDueAt) : '—'}
              />
              <Kv k="요청자" v={ticket.requester.name} />
            </div>
          </div>
        </section>

        {result?.missingFields && result.missingFields.length > 0 && (
          <section className="mt-9">
            <h3 className="text-[15px] font-semibold mb-3">누락된 정보</h3>
            <ul className="border-t border-line pt-3 text-[17px] space-y-2">
              {result.missingFields.map((f) => (
                <li key={f} className="text-mute">
                  — {label.field(f)}
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>
    </article>
  )
}

function Kv({ k, v }: { k: string; v: string }) {
  return (
    <div
      className="grid border-t border-line py-3 text-[17px]"
      style={{ gridTemplateColumns: '120px 1fr' }}
    >
      <span className="text-[15px] text-mute">{k}</span>
      <span>{v}</span>
    </div>
  )
}
