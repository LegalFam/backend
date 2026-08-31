package com.legalfam.backend.payment.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.legalfam.backend.payment.domain.exception.PaymentWebhookException;
import com.legalfam.backend.payment.infrastructure.config.MercadoPagoProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MercadoPagoPaymentGatewayAdapterTest {

    @Test
    void parseVerifiedWebhookRejectsMissingSignatureWhenSecretIsConfigured() {
        MercadoPagoPaymentGatewayAdapter adapter = new MercadoPagoPaymentGatewayAdapter(
                mock(ObjectMapper.class),
                new MercadoPagoProperties(
                        "access-token",
                        "https://api.mercadopago.com",
                        "webhook-secret",
                        "http://localhost:3000/billing/success",
                        "http://localhost:3000/billing/cancel"
                )
        );

        assertThrows(PaymentWebhookException.class, () -> adapter.parseVerifiedWebhook(
                "{\"id\":\"evt_123\"}",
                null,
                "request-123",
                "evt_123"
        ));
    }

    @Test
    void parseVerifiedWebhookRejectsPayloadWhenSecretIsNotConfigured() {
        MercadoPagoPaymentGatewayAdapter adapter = new MercadoPagoPaymentGatewayAdapter(
                mock(ObjectMapper.class),
                new MercadoPagoProperties(
                        "access-token",
                        "https://api.mercadopago.com",
                        "   ",
                        "http://localhost:3000/billing/success",
                        "http://localhost:3000/billing/cancel"
                )
        );

        assertThrows(PaymentWebhookException.class, () -> adapter.parseVerifiedWebhook(
                "{\"id\":\"evt_123\"}",
                "ts=1,v1=deadbeef",
                "request-123",
                "evt_123"
        ));
    }
}
