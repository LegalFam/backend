package com.legalfam.backend.auth.application.service;

public record AuthLinkProperties(
        String frontendBaseUrl,
        String verifyEmailPath,
        String resetPasswordPath
) { }
