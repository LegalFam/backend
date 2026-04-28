package com.legalfam.backend.auth.infrastructure.config;

import com.legalfam.backend.auth.application.service.AuthTokenProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {

    @Bean
    public AuthTokenProperties authTokenProperties(
            @Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs
    ) {
        return new AuthTokenProperties(refreshTokenExpirationMs);
    }
}
