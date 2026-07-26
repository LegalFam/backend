package com.legalfam.backend.auth.infrastructure.config;

import com.legalfam.backend.auth.application.service.AuthLinkProperties;
import com.legalfam.backend.auth.application.service.AuthTokenProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {

    @Bean
    public AuthTokenProperties authTokenProperties(
            JwtProperties jwtProperties,
            AuthTokenTtlProperties authTokenTtlProperties
    ) {
        return new AuthTokenProperties(
                jwtProperties.refreshTokenExpirationMs(),
                authTokenTtlProperties.emailVerificationExpirationMs(),
                authTokenTtlProperties.passwordResetExpirationMs(),
                authTokenTtlProperties.mailResendCooldownMs()
        );
    }

    @Bean
    public AuthLinkProperties authLinkProperties(FrontendProperties frontendProperties) {
        return new AuthLinkProperties(
                frontendProperties.baseUrl(),
                frontendProperties.verifyEmailPath(),
                frontendProperties.resetPasswordPath()
        );
    }
}
