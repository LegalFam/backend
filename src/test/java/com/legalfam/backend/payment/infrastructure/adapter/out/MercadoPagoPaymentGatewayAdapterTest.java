package com.legalfam.backend.payment.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.legalfam.backend.payment.domain.exception.PaymentWebhookException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MercadoPagoPaymentGatewayAdapterTest {

    @Test
    void parseVerifiedWebhookRejectsMissingSignatureWhenSecretIsConfigured() {
        MercadoPagoPaymentGatewayAdapter adapter = new MercadoPagoPaymentGatewayAdapter(
                mock(ObjectMapper.class),
                "access-token",
                "https://api.mercadopago.com",
                "webhook-secret"
        );

        assertThrows(PaymentWebhookException.class, () -> adapter.parseVerifiedWebhook(
                "{\"id\":\"evt_123\"}",
                null,
                "request-123",
                "evt_123"
        ));
    }
}
