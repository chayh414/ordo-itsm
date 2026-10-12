import type { AssistantCardGet } from '../../../lib/types'
import { formatWhen, label } from '../../../lib/i18n'

/*
 * get_request 상세. 읽기 전용 요청서.
 */

interface Props {
  card: AssistantCardGet
}

export default function GetDoc({ card }: Props) {
  const { ticket, triage } = card
  const result = triage?.result ?? null

  const factorPoints = triage?.factorPoints ?? null
  const riskRows = (result?.riskFactors ?? []).map((f) => {
    const ev = result?.evidence?.find((e) => e.factor === f)
    return {
      factor: f,
      quote: ev?.quote ?? null,
      points: factorPoints ? factorPoints[f] ?? null : null,
    }
  })
  const grade = triage?.riskGrade ?? null
  const score = triage?.riskScore ?? null
  const isHighRisk = grade === 'HIGH' || grade === 'CRITICAL'

  return (
    <article className="h-full flex flex-col overflow-y-auto px-16 pt-9 pb-10">
      <div className="flex justify-between items-baseline">
        <span className="text-[13px] text-mute">{label.docHeader(ticket.status)}</span>
        <span className="text-[13px] text-mute">
          <b className="text-ink font-semibold">{ticket.ticketNo}</b>
        </span>
      </div>

      <h1 className="text-[34px] leading-[1.3] font-bold tracking-[-0.015em] mt-4 mb-7 max-w-[640px]">
        {ticket.subject}
      </h1>

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

      {(riskRows.length > 0 || grade) && (
        <section className="mt-7 grid gap-x-6" style={{ gridTemplateColumns: '180px 1fr' }}>
          <div>
            <h3 className="text-[15px] font-semibold">
              위험도
              <small className="block font-normal text-mute text-[13px] mt-0.5">규칙에 따라 계산</small>
            </h3>
            {grade && score != null && (
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
            )}
          </div>
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
        </section>
      )}

      {result?.missingFields && result.missingFields.length > 0 && (
        <section className="mt-7 grid gap-x-6" style={{ gridTemplateColumns: '180px 1fr' }}>
          <div>
            <h3 className="text-[15px] font-semibold">누락된 정보</h3>
          </div>
          <div>
            <ul className="border-t border-line pt-2.5 text-[15px] space-y-1.5">
              {result.missingFields.map((f) => (
                <li key={f} className="text-mute">
                  — {label.field(f)}
                </li>
              ))}
            </ul>
          </div>
        </section>
      )}
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
