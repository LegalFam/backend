package com.legalfam.backend.auth.application.port.in;

import com.legalfam.backend.auth.application.dto.TokenResponse;
import com.legalfam.backend.auth.application.dto.UserResponse;
import java.util.UUID;

public interface IAuthUseCase {
    TokenResponse signup(String email, String rawPassword, String name, String phone);

    TokenResponse login(String email, String rawPassword);

    TokenResponse refresh(String refreshTokenValue);

    UserResponse getProfile(UUID userId);

    UserResponse updateProfile(UUID userId, String name);

    void updatePassword(UUID userId, String currentRawPassword, String newRawPassword);
}
