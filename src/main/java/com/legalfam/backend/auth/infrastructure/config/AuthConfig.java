package com.legalfam.backend.auth.infrastructure.config;

import com.legalfam.backend.auth.application.service.AuthTokenProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {

    @Bean
    public AuthTokenProperties authTokenProperties(JwtProperties jwtProperties) {
        return new AuthTokenProperties(jwtProperties.refreshTokenExpirationMs());
    }
}
