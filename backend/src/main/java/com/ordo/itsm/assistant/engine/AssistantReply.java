package com.ordo.itsm.assistant.engine;

import java.util.List;

/** LLM 응답의 단일 메시지. content 가 있거나 toolCalls 가 있거나 둘 다 가능. */
public record AssistantReply(String content, List<ToolCall> toolCalls) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
