package com.legalfam.backend.payment.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MercadoPagoPropertiesTest {

    private static MercadoPagoProperties withCheckoutUrls(String successUrl, String cancelUrl) {
        return new MercadoPagoProperties(
                "access-token",
                "https://api.mercadopago.com",
                "webhook-secret",
                successUrl,
                cancelUrl
        );
    }

    @Test
    void collapsesDuplicateSlashesWhenFrontendBaseUrlEndsWithSlash() {
        MercadoPagoProperties properties = withCheckoutUrls(
                "https://legalfam.web.app//billing/success",
                "https://legalfam.web.app//billing/cancel"
        );

        assertEquals("https://legalfam.web.app/billing/success", properties.normalizedCheckoutSuccessUrl());
        assertEquals("https://legalfam.web.app/billing/cancel", properties.normalizedCheckoutCancelUrl());
    }

    @Test
    void keepsSchemeSeparatorAndAlreadyCleanUrlsUntouched() {
        MercadoPagoProperties properties = withCheckoutUrls(
                "  http://localhost:3000/billing/success  ",
                "https://legalfam.web.app/billing/cancel"
        );

        assertEquals("http://localhost:3000/billing/success", properties.normalizedCheckoutSuccessUrl());
        assertEquals("https://legalfam.web.app/billing/cancel", properties.normalizedCheckoutCancelUrl());
    }
}
