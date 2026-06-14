package com.legalfam.backend.auth.application.port.out;

import java.util.UUID;

public interface ITokenValidationPort {
    UUID extractUserId(String token);

    boolean isTokenValid(String token);
}
