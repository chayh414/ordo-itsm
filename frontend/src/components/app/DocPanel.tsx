import type { AssistantCard, AssistantCardCreate, AssistantCardGet, PageResponse, RiskGrade, TicketSummaryResponse } from '../../lib/types'
import EmptyDoc from './doc/EmptyDoc'
import CreateDoc from './doc/CreateDoc'
import ListDoc from './doc/ListDoc'
import GetDoc from './doc/GetDoc'

/*
 * 어떤 카드가 들어왔는지에 따라 적절한 문서형 뷰로 분기.
 * create_request 는 submit·supplement 상태를 바깥에서 받아 CreateDoc 에 넘긴다.
 */

interface Props {
  card: AssistantCard | null
  requesterName: string
  submitting: boolean
  submitted: boolean
  onCancel(): void
  onSubmit(): void
  onSupplement(field: string, answer: string): void
  supplementingField: string | null
  supplementedAnswers: Record<string, string>
  prevRisk: { grade: RiskGrade | null; score: number | null } | null
  isProviderSide: boolean
}

export default function DocPanel({
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
  isProviderSide,
}: Props) {
  if (!card) return <EmptyDoc />

  switch (card.tool) {
    case 'create_request':
      return (
        <CreateDoc
          card={card.data as AssistantCardCreate}
          requesterName={requesterName}
          submitting={submitting}
          submitted={submitted}
          onCancel={onCancel}
          onSubmit={onSubmit}
          onSupplement={onSupplement}
          supplementingField={supplementingField}
          supplementedAnswers={supplementedAnswers}
          prevRisk={prevRisk}
        />
      )
    case 'list_my_requests':
    case 'list_requests':
      return (
        <ListDoc
          page={card.data as PageResponse<TicketSummaryResponse>}
          showTenant={isProviderSide}
        />
      )
    case 'get_request':
      return <GetDoc card={card.data as AssistantCardGet} />
    default:
      return <EmptyDoc />
  }
}
