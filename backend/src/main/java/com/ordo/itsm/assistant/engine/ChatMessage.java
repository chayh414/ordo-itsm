package com.ordo.itsm.assistant.engine;

import java.util.List;

/**
 * OpenAI 호환 chat message 1개.
 *
 * - system/user: content 만
 * - assistant: content (null 가능) + 선택 tool_calls
 * - tool: tool_call_id + content
 */
public record ChatMessage(String role, String content, String toolCallId, List<ToolCall> toolCalls) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", content, null, toolCalls);
    }

    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, toolCallId, null);
    }
}
