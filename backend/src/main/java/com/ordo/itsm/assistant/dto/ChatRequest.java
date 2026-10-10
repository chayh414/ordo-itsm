package com.ordo.itsm.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 어시스턴트 채팅 요청. history 는 클라이언트가 매 턴 재전송한다 (서버는 영속화 안 함).
 * - message: 이번 턴 사용자 발화
 * - history: 지금까지의 user/assistant 왕복 (최대 20개까지만 서버가 사용)
 */
public record ChatRequest(
        @NotBlank @Size(max = 5000) String message,
        List<HistoryMessage> history
) {
    public record HistoryMessage(String role, String content) {
    }
}
