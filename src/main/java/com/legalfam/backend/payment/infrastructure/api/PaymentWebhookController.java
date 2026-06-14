package com.legalfam.backend.payment.infrastructure.api;

import com.legalfam.backend.payment.application.port.in.IPaymentUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/webhook")
@Tag(name = "Payments")
public class PaymentWebhookController {

    private final IPaymentUseCase IPaymentUseCase;

    public PaymentWebhookController(IPaymentUseCase IPaymentUseCase) {
        this.IPaymentUseCase = IPaymentUseCase;
    }

    @PostMapping("/mercado-pago")
    @Operation(summary = "Receive Mercado Pago webhook events")
    public ResponseEntity<Void> receiveWebhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(name = "X-Signature", required = false) String signatureHeader,
            @RequestHeader(name = "X-Request-Id", required = false) String requestId,
            @RequestParam Map<String, String> queryParams
    ) {
        IPaymentUseCase.handleWebhook(payload, signatureHeader, requestId, firstNonBlank(
                queryParams.get("data.id"),
                queryParams.get("id")
        ));
        return ResponseEntity.ok().build();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }
}
