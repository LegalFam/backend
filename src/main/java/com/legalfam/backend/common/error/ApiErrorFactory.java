package com.legalfam.backend.common.error;

import java.time.Instant;
import org.springframework.http.HttpStatus;

public final class ApiErrorFactory {

    private ApiErrorFactory() {
    }

    public static ApiError build(HttpStatus status, String type, String code, String message, String path) {
        return new ApiError(
                type,
                code,
                message,
                status.value(),
                path,
                Instant.now().toString()
        );
    }
}
