import type { PageResponse, TicketSummaryResponse } from '../../../lib/types'
import { formatWhen, label } from '../../../lib/i18n'

/*
 * list_my_requests / list_requests 결과.
 * 선 행: 번호 · 티켓번호 · 제목 · 상태 · SLA 마감
 */

interface Props {
  page: PageResponse<TicketSummaryResponse>
  showTenant: boolean   // OPERATOR 뷰에서는 테넌트명 표시
}

export default function ListDoc({ page, showTenant }: Props) {
  return (
    <article className="h-full flex flex-col overflow-y-auto px-16 pt-9 pb-10">
      <div className="flex justify-between items-baseline mb-7">
        <span className="text-[13px] text-mute">요청 목록 · {page.totalElements}건</span>
      </div>

      {page.content.length === 0 ? (
        <p className="text-[15px] text-mute">조건에 해당하는 요청이 없습니다.</p>
      ) : (
        <div>
          {page.content.map((t, i) => (
            <div
              key={t.id}
              className="grid border-t border-line py-4 text-[15px] items-baseline"
              style={{
                gridTemplateColumns: showTenant
                  ? '36px 160px 140px 1fr 140px 180px'
                  : '36px 160px 1fr 140px 180px',
              }}
            >
              <span className="text-[13px] text-faint">{String(i + 1).padStart(2, '0')}</span>
              <span>{t.ticketNo}</span>
              {showTenant && <span className="text-mute">{t.tenantName}</span>}
              <span>{t.subject}</span>
              <span className="text-mute">{label.status(t.status)}</span>
              <span className="text-right text-mute">
                {t.sla?.resolutionDueAt ? `~ ${formatWhen(t.sla.resolutionDueAt)}` : '—'}
              </span>
            </div>
          ))}
          {page.content.length > 0 && <div className="border-t border-line" />}
        </div>
      )}
    </article>
  )
}
