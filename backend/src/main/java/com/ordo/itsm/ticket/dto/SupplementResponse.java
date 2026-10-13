package com.ordo.itsm.ticket.dto;

import com.ordo.itsm.triage.dto.TriageResponse;

/**
 * 보완 처리 후 응답. 프론트 어시스턴트 카드 (AssistantCardCreate/Get) 와 동일한 { ticket, triage } 셰이프.
 * 재분류·위험도 재평가 후 갱신된 상태를 한 번에 돌려준다.
 */
public record SupplementResponse(TicketDetailResponse ticket, TriageResponse triage) {
}
