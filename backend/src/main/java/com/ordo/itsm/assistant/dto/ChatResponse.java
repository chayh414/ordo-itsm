package com.ordo.itsm.assistant.dto;

import java.util.List;
import java.util.Map;

/**
 * 어시스턴트 응답.
 * - reply: 자연어 답변
 * - cards: 프론트가 카드로 렌더할 구조화 결과 (각 도구 호출 1건당 1개)
 * - toolCalls: 디버그·감사용 호출 요약
 * - truncated: 3회 상한에 걸려 강제 종료됐으면 true
 */
public record ChatResponse(
        String reply,
        List<Card> cards,
        List<ToolCallSummary> toolCalls,
        boolean truncated
) {
    public record Card(String tool, Object data) {
    }

    public record ToolCallSummary(String name, Map<String, Object> args, boolean ok, String errorMessage) {
    }
}
