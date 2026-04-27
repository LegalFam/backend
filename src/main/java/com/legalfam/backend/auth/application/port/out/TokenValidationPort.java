package com.legalfam.backend.auth.application.port.out;

import java.util.UUID;

public interface TokenValidationPort {
    UUID extractUserId(String token);

    boolean isTokenValid(String token);
}
