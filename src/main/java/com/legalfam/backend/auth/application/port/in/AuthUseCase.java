package com.legalfam.backend.auth.application.port.in;

import com.legalfam.backend.auth.application.dto.TokenResponse;

public interface AuthUseCase {
    TokenResponse signup(String email, String rawPassword, String name, String phone);

    TokenResponse login(String email, String rawPassword);

    TokenResponse refresh(String refreshTokenValue);
}
