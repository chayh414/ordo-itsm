package com.ordo.itsm.global.exception;

import java.time.OffsetDateTime;

public record ErrorResponse(String code, String message, OffsetDateTime timestamp) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), OffsetDateTime.now());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message, OffsetDateTime.now());
    }

    /** 시큐리티 필터 단계(컨트롤러 밖)에서 직접 응답을 쓸 때 사용 */
    public String toJson() {
        return "{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"timestamp\":\"" + timestamp + "\"}";
    }
}
