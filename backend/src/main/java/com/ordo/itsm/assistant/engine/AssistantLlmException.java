package com.ordo.itsm.assistant.engine;

/** LLM 호출 실패. AssistantService 가 캐치해 안전한 폴백 응답으로 변환한다. */
public class AssistantLlmException extends RuntimeException {

    public AssistantLlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
