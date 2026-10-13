package com.ordo.itsm.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 보완 요청 — 어떤 필드(key)에 어떤 답변을 넣었는지. */
public record SupplementRequest(
        @NotBlank @Size(max = 50) String field,
        @NotBlank @Size(max = 2000) String answer
) {
}
