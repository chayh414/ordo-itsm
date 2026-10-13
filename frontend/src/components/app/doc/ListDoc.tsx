import type { PageResponse, TicketSummaryResponse } from '../../../lib/types'
import { formatWhen, label } from '../../../lib/i18n'

/*
 * list_my_requests / list_requests 결과.
 * 선 행: 번호 · 티켓번호 · 제목 · 상태 · SLA 마감.
 * 글자: 본문 17px, 번호·메타 14px (14px 미만 금지, 회색 최소 #6F6F6F).
 */

interface Props {
  page: PageResponse<TicketSummaryResponse>
  showTenant: boolean
}

export default function ListDoc({ page, showTenant }: Props) {
  return (
    <article className="h-full overflow-y-auto">
      <div className="mx-auto max-w-[920px] px-16 pt-9 pb-10">
        <div className="flex justify-between items-baseline mb-7">
          <span className="text-[14px] text-mute">요청 목록 · {page.totalElements}건</span>
        </div>

        {page.content.length === 0 ? (
          <p className="text-[17px] text-mute">조건에 해당하는 요청이 없습니다.</p>
        ) : (
          <div>
            {page.content.map((t, i) => (
              <div
                key={t.id}
                className="grid border-t border-line py-4 text-[17px] items-baseline"
                style={{
                  gridTemplateColumns: showTenant
                    ? '40px 180px 140px 1fr 140px 180px'
                    : '40px 180px 1fr 140px 180px',
                }}
              >
                <span className="text-[14px] text-mute">{String(i + 1).padStart(2, '0')}</span>
                <span>{t.ticketNo}</span>
                {showTenant && <span className="text-mute">{t.tenantName}</span>}
                <span>{t.subject}</span>
                <span className="text-mute">{label.status(t.status)}</span>
                <span className="text-right text-mute">
                  {t.sla?.resolutionDueAt ? `~ ${formatWhen(t.sla.resolutionDueAt)}` : '—'}
                </span>
              </div>
            ))}
            <div className="border-t border-line" />
          </div>
        )}
      </div>
    </article>
  )
}
