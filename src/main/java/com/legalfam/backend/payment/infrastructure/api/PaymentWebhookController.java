package com.legalfam.backend.payment.infrastructure.api;

import com.legalfam.backend.payment.application.port.in.PaymentUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/webhook")
@Tag(name = "Payments")
public class PaymentWebhookController {

    private final PaymentUseCase paymentUseCase;

    public PaymentWebhookController(PaymentUseCase paymentUseCase) {
        this.paymentUseCase = paymentUseCase;
    }

    @PostMapping("/mercado-pago")
    @Operation(summary = "Receive Mercado Pago webhook events")
    public ResponseEntity<Void> receiveWebhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(name = "X-Signature", required = false) String signatureHeader
    ) {
        paymentUseCase.handleWebhook(payload, signatureHeader);
        return ResponseEntity.ok().build();
    }
}
