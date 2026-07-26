package com.legalfam.backend.auth.application.service;

public record AuthTokenProperties(
        long refreshTokenExpirationMs,
        long emailVerificationExpirationMs,
        long passwordResetExpirationMs,
        long mailResendCooldownMs
) { }
