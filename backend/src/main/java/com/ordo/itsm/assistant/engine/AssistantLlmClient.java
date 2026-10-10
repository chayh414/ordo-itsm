package com.ordo.itsm.assistant.engine;

import java.util.List;

/**
 * 어시스턴트의 LLM 호출 추상화.
 * 운영 구현은 OpenAI 호환 /v1/chat/completions 를 호출하는 HttpAssistantLlmClient.
 * 테스트는 fake 를 주입해 루프 동작을 검증한다.
 */
public interface AssistantLlmClient {

    AssistantReply chat(List<ChatMessage> messages, List<ToolSpec> tools);
}
