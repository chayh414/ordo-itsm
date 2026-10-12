package com.ordo.itsm.assistant.engine;

import java.util.List;

/**
 * LLM 응답의 단일 메시지. content 가 있거나 toolCalls 가 있거나 둘 다 가능.
 * promptTokens 는 서버가 usage.prompt_tokens 로 돌려준 값 (없으면 null). 컨텍스트 압박 모니터링용.
 */
public record AssistantReply(String content, List<ToolCall> toolCalls, Integer promptTokens) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
