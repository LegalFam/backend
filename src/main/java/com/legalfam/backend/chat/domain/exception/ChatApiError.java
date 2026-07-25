package com.legalfam.backend.chat.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public enum ChatApiError implements ApiErrorDescriptor {
    MESSAGE_REQUIRED("validation_error", "message_required", 400, "Message is required"),
    MESSAGE_TOO_LONG("validation_error", "message_too_long", 400, "Message must be at most 4000 characters"),
    SESSION_ID_REQUIRED("validation_error", "session_id_required", 400, "Session id is required"),
    SESSION_TITLE_REQUIRED("validation_error", "session_title_required", 400, "Session title is required"),
    SESSION_TITLE_TOO_LONG("validation_error", "session_title_too_long", 400, "Session title must be at most 120 characters"),
    RATING_REQUIRED("validation_error", "rating_required", 400, "Rating is required"),
    RATING_OUT_OF_RANGE("validation_error", "rating_out_of_range", 400, "Rating must be between 1 and 5"),
    FEEDBACK_COMMENT_TOO_LONG("validation_error", "feedback_comment_too_long", 400, "Feedback comment must be at most 1000 characters"),
    CHAT_SESSION_NOT_FOUND("resource_error", "chat_session_not_found", 404, "Chat session not found"),
    CHAT_MESSAGE_NOT_FOUND("resource_error", "chat_message_not_found", 404, "Chat message not found"),
    ASSISTANT_DELIVERY_EVENT_NOT_FOUND("resource_error", "assistant_delivery_event_not_found", 404, "Assistant delivery event not found"),
    MESSAGE_PROCESSING_PENDING("chat_state_error", "message_processing_pending", 409, "Message processing is already pending"),
    ASSISTANT_RECEIPT_PENDING("chat_state_error", "assistant_receipt_pending", 409, "Assistant receipt confirmation is still pending for this session"),
    INSUFFICIENT_TOKENS("payment_error", "insufficient_tokens", 403, "You do not have enough tokens to send a message"),
    PERSONAL_DATA_NOT_ALLOWED("validation_error", "personal_data_not_allowed", 400, "Personal data is not allowed in chat messages"),
    METADATA_ONLY_ASSISTANT("validation_error", "metadata_only_assistant", 400, "Metadata can only be applied to assistant messages"),
    ONLY_ASSISTANT_MESSAGES_CAN_BE_RATED("validation_error", "only_assistant_messages_can_be_rated", 400, "Only assistant messages can be rated"),
    RECEIPT_ONLY_ASSISTANT_MESSAGES("validation_error", "receipt_only_assistant_messages", 400, "Receipt can only be confirmed for assistant messages"),
    CURSOR_INVALID("validation_error", "cursor_invalid", 400, "Cursor query is invalid"),
    UPSTREAM_ERROR("upstream_error", "upstream_error", 502, "Assistant service failed to prepare a response"),
    UPSTREAM_TIMEOUT("upstream_error", "upstream_timeout", 502, "Assistant service timed out"),
    UPSTREAM_EMPTY_RESPONSE("upstream_error", "upstream_empty_response", 502, "Assistant service returned an empty response"),
    UPSTREAM_INVALID_RESPONSE("upstream_error", "upstream_invalid_response", 502, "Assistant service returned an invalid response"),
    UPSTREAM_NOT_CONFIGURED("upstream_error", "upstream_not_configured", 502, "Assistant service is not configured"),
    UPSTREAM_UNAVAILABLE("upstream_error", "upstream_unavailable", 502, "Assistant service is unavailable"),
    UPSTREAM_REQUEST_INVALID("upstream_error", "upstream_request_invalid", 502, "Assistant service request could not be prepared"),
    AGENT_VALIDATION_FAILED("upstream_error", "agent_validation_failed", 502, "Assistant response validation failed");

    private final String type;
    private final String code;
    private final int status;
    private final String message;

    ChatApiError(String type, String code, int status, String message) {
        this.type = type;
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public String type() { return type; }
    public String code() { return code; }
    public int status() { return status; }
    public String message() { return message; }
}
