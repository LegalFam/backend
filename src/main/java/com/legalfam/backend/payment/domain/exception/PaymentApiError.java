package com.legalfam.backend.payment.domain.exception;

import com.legalfam.backend.common.error.ApiErrorDescriptor;

public enum PaymentApiError implements ApiErrorDescriptor {
    PLAN_CODE_REQUIRED("validation_error", "plan_code_required", 400, "Plan code is required"),
    PLAN_CODE_TOO_LONG("validation_error", "plan_code_too_long", 400, "Plan code must be at most 40 characters"),
    SUCCESS_URL_TOO_LONG("validation_error", "success_url_too_long", 400, "Success URL must be at most 2048 characters"),
    SUCCESS_URL_INVALID("validation_error", "success_url_invalid", 400, "Success URL must be an HTTP URL"),
    CANCEL_URL_TOO_LONG("validation_error", "cancel_url_too_long", 400, "Cancel URL must be at most 2048 characters"),
    CANCEL_URL_INVALID("validation_error", "cancel_url_invalid", 400, "Cancel URL must be an HTTP URL"),
    CHECKOUT_REQUEST_REQUIRED("validation_error", "checkout_request_required", 400, "Checkout request body is required"),
    WEBHOOK_PAYLOAD_REQUIRED("payment_error", "webhook_payload_required", 400, "Webhook payload is required"),
    PAID_PLAN_REQUIRED("validation_error", "paid_plan_required", 400, "Paid plan is required"),
    PLAN_NOT_PURCHASABLE("validation_error", "plan_not_purchasable", 400, "Selected plan is not configured for checkout"),
    PLAN_CODE_INVALID("validation_error", "plan_code_invalid", 400, "Plan code is invalid"),
    CHECKOUT_PLAN_ALREADY_ACTIVE("payment_error", "checkout_plan_already_active", 403, "User is already subscribed to the selected plan"),
    CHECKOUT_ACTIVE_GATEWAY_SUBSCRIPTION("payment_error", "checkout_active_gateway_subscription", 403, "Cancel the current gateway subscription before changing plans"),
    NO_GATEWAY_SUBSCRIPTION_TO_CANCEL("payment_error", "no_gateway_subscription_to_cancel", 400, "No gateway subscription is available to cancel"),
    SUBSCRIPTION_ALREADY_CANCELED("payment_error", "subscription_already_canceled", 400, "Subscription is already scheduled for cancellation"),
    SUBSCRIPTION_NOT_FOUND("payment_error", "subscription_not_found", 404, "Subscription not found"),
    SUBSCRIPTION_INACTIVE("payment_error", "subscription_inactive", 403, "Subscription is not active"),
    INSUFFICIENT_TOKENS("payment_error", "insufficient_tokens", 403, "Insufficient tokens"),
    PAYMENT_WEBHOOK_UNMATCHED_USER("payment_error", "payment_webhook_unmatched_user", 400, "Payment webhook cannot be matched to a local user"),
    WEBHOOK_PAYLOAD_INVALID("payment_error", "webhook_payload_invalid", 400, "Webhook payload is invalid"),
    WEBHOOK_USER_REFERENCE_INVALID("payment_error", "webhook_user_reference_invalid", 400, "Webhook user reference is invalid"),
    WEBHOOK_REQUEST_ID_REQUIRED("payment_error", "webhook_request_id_required", 400, "Webhook request id is required"),
    WEBHOOK_DATA_ID_REQUIRED("payment_error", "webhook_data_id_required", 400, "Webhook data id is required"),
    WEBHOOK_SIGNATURE_INVALID("payment_error", "webhook_signature_invalid", 400, "Webhook signature is invalid"),
    WEBHOOK_SIGNATURE_REQUIRED("payment_error", "webhook_signature_required", 400, "Webhook signature is required"),
    WEBHOOK_SIGNATURE_UNVERIFIABLE("payment_error", "webhook_signature_unverifiable", 400, "Webhook signature cannot be verified"),
    WEBHOOK_SECRET_NOT_CONFIGURED("payment_error", "webhook_secret_not_configured", 503, "Webhook signing secret is not configured"),
    PAYMENT_GATEWAY_UNAVAILABLE("payment_error", "payment_gateway_unavailable", 503, "Payment gateway is unavailable"),
    PAYMENT_GATEWAY_EMPTY_RESPONSE("payment_error", "payment_gateway_empty_response", 503, "Payment gateway returned an empty response"),
    PAYMENT_GATEWAY_MISCONFIGURED("payment_error", "payment_gateway_misconfigured", 503, "Payment gateway is misconfigured"),
    PAYMENT_GATEWAY_PAYER_EMAIL_REQUIRED("payment_error", "payment_gateway_payer_email_required", 503, "Payment gateway requires a payer email"),
    PAYMENT_GATEWAY_CHECKOUT_URL_MISSING("payment_error", "payment_gateway_checkout_url_missing", 503, "Payment gateway did not return a checkout URL"),
    PAYMENT_GATEWAY_SUBSCRIPTION_ID_REQUIRED("payment_error", "payment_gateway_subscription_id_required", 503, "Payment gateway subscription id is required");

    private final String type;
    private final String code;
    private final int status;
    private final String message;

    PaymentApiError(String type, String code, int status, String message) {
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
