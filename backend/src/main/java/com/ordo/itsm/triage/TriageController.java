package com.ordo.itsm.triage;

import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.triage.dto.TriageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets/{ticketId}/triage")
@RequiredArgsConstructor
public class TriageController {

    private final TriageService triageService;

    /** 재분석 (등록 시에는 자동 실행됨) */
    @PostMapping
    public TriageResponse rerun(@AuthenticationPrincipal AuthUser user, @PathVariable Long ticketId) {
        return triageService.run(user, ticketId);
    }

    @GetMapping
    public TriageResponse latest(@AuthenticationPrincipal AuthUser user, @PathVariable Long ticketId) {
        return triageService.latest(user, ticketId);
    }
}
