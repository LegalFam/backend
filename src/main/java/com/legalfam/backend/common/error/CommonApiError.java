package com.legalfam.backend.common.error;

public enum CommonApiError implements ApiErrorDescriptor {
    MALFORMED_JSON("validation_error", "malformed_json", 400, "Malformed request body"),
    INVALID_REQUEST("validation_error", "invalid_request", 400, "Request validation failed"),
    UNAUTHORIZED("authentication_error", "unauthorized", 401, "Authentication is required"),
    FORBIDDEN("authorization_error", "forbidden", 403, "Access is forbidden"),
    MAX_UPLOAD_SIZE_EXCEEDED("validation_error", "max_upload_size_exceeded", 413, "File exceeds configured upload size limit"),
    INTERNAL_SERVER_ERROR("internal_error", "internal_server_error", 500, "An unexpected error occurred");

    private final String type;
    private final String code;
    private final int status;
    private final String message;

    CommonApiError(String type, String code, int status, String message) {
        this.type = type;
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public String type() {
        return type;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public String message() {
        return message;
    }
}
