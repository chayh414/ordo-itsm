package com.ordo.itsm.auth;

import com.ordo.itsm.auth.dto.LoginRequest;
import com.ordo.itsm.auth.dto.LoginResponse;
import com.ordo.itsm.auth.dto.MeResponse;
import com.ordo.itsm.global.exception.BusinessException;
import com.ordo.itsm.global.exception.ErrorCode;
import com.ordo.itsm.global.security.JwtProvider;
import com.ordo.itsm.user.User;
import com.ordo.itsm.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public LoginResponse login(LoginRequest request) {
        // 이메일 존재 여부를 노출하지 않도록 실패 사유는 하나로 통일
        User user = userRepository.findByEmail(request.email())
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        String token = jwtProvider.createToken(user);
        return LoginResponse.bearer(token, jwtProvider.getExpirationSeconds());
    }

    public MeResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return MeResponse.from(user);
    }
}
