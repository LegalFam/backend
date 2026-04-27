package com.legalfam.backend.common.error;

public record ApiError(
        String type,
        String code,
        String message,
        int status,
        String path,
        String timestamp
) {
}
