package com.ordo.itsm.assistant.tool;

/**
 * 어시스턴트 도구 실행 결과.
 * - llmText: LLM 에게 되돌려줄 요약 문자열 (한국어). 실패 시 사유 메시지.
 * - cardData: 프론트가 카드로 그릴 수 있는 구조화 데이터 (null 허용).
 * - ok=false 일 때 errorMessage 에 사용자가 보기 좋은 사유.
 */
public record ToolResult(boolean ok, String llmText, Object cardData, String errorMessage) {

    public static ToolResult ok(String llmText, Object cardData) {
        return new ToolResult(true, llmText, cardData, null);
    }

    public static ToolResult error(String message) {
        return new ToolResult(false, message, null, message);
    }
}
