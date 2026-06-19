package com.legalfam.backend.chat.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;
import com.legalfam.backend.common.error.CommonApiError;

public class ChatAccessDeniedException extends RuntimeException {
    private final ApiErrorDescriptor error;

    private ChatAccessDeniedException() {
        this(CommonApiError.FORBIDDEN);
    }

    private ChatAccessDeniedException(ApiErrorDescriptor error) {
        super(error.message());
        this.error = error;
    }

    public static ChatAccessDeniedException forbidden() {
        return new ChatAccessDeniedException();
    }

    public static ChatAccessDeniedException of(ApiErrorDescriptor error) {
        return new ChatAccessDeniedException(error);
    }

    public ApiErrorDescriptor error() {
        return error;
    }
}
