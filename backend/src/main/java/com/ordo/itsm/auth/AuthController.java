package com.ordo.itsm.auth;

import com.ordo.itsm.auth.dto.LoginRequest;
import com.ordo.itsm.auth.dto.LoginResponse;
import com.ordo.itsm.auth.dto.MeResponse;
import com.ordo.itsm.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser authUser) {
        return authService.me(authUser.userId());
    }
}
