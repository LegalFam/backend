package com.legalfam.backend.error;

public record ApiError(
        String type,
        String code,
        String message,
        int status,
        String path,
        String timestamp
) {
}
