package com.ordo.itsm.triage.engine;

import lombok.Getter;

@Getter
public class TriageEngineException extends RuntimeException {

    public enum Kind {
        INVALID_JSON,   // 응답은 왔지만 형식이 스키마와 다름
        FAILED          // 호출 실패 (네트워크, 인증, 한도 초과 등)
    }

    private final Kind kind;
    private final String rawResponse;

    public TriageEngineException(Kind kind, String message, String rawResponse, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.rawResponse = rawResponse;
    }
}
