package com.ordo.itsm.assistant;

import com.ordo.itsm.assistant.dto.ChatRequest;
import com.ordo.itsm.assistant.dto.ChatResponse;
import com.ordo.itsm.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    /**
     * 사내 AI 어시스턴트 대화.
     * - 사용자 발화와 선택적 history 를 받아 자연어 답변 + 카드 데이터를 반환.
     * - 역할별 도구 세트는 서버가 결정 (요청에서 지정 불가).
     */
    @PostMapping("/chat")
    public ChatResponse chat(@AuthenticationPrincipal AuthUser user,
                             @Valid @RequestBody ChatRequest request) {
        return assistantService.chat(user, request);
    }
}
