package com.legalfam.backend.auth.application.port.in;

import com.legalfam.backend.auth.application.dto.AuthMailDispatch;
import com.legalfam.backend.auth.application.dto.TokenResponse;
import com.legalfam.backend.auth.application.dto.UserResponse;
import java.util.Optional;
import java.util.UUID;

public interface IAuthUseCase {
    UserResponse signup(String email, String rawPassword, String name, String phone);

    TokenResponse login(String email, String rawPassword);

    TokenResponse refresh(String refreshTokenValue);

    UserResponse getProfile(UUID userId);

    UserResponse updateProfile(UUID userId, String name);

    void updatePassword(UUID userId, String currentRawPassword, String newRawPassword);

    Optional<AuthMailDispatch> issueEmailVerificationToken(UUID userId);

    Optional<AuthMailDispatch> issueEmailVerificationToken(String email);

    Optional<AuthMailDispatch> issuePasswordResetToken(String email);

    void confirmEmailVerification(String rawToken);

    void resetPassword(String rawToken, String newRawPassword);
}
