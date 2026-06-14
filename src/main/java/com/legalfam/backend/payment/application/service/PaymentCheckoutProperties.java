package com.legalfam.backend.payment.application.service;

public record PaymentCheckoutProperties(
        String defaultCheckoutSuccessUrl,
        String defaultCheckoutCancelUrl
) {
}
