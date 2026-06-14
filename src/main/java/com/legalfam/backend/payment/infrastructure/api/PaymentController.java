package com.legalfam.backend.payment.infrastructure.api;

import com.legalfam.backend.common.error.ApiError;
import com.legalfam.backend.payment.application.dto.CreateCheckoutSessionRequest;
import com.legalfam.backend.payment.application.dto.CreateCheckoutSessionResponse;
import com.legalfam.backend.payment.application.dto.PaymentPlanResponse;
import com.legalfam.backend.payment.application.dto.PaymentSubscriptionResponse;
import com.legalfam.backend.payment.application.port.in.IPaymentUseCase;
import com.legalfam.backend.payment.domain.exception.InvalidPaymentRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final IPaymentUseCase IPaymentUseCase;

    public PaymentController(IPaymentUseCase IPaymentUseCase) {
        this.IPaymentUseCase = IPaymentUseCase;
    }

    @GetMapping("/plans")
    @Operation(summary = "List available subscription plans")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plans fetched",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PaymentPlanResponse.class))))
    })
    public ResponseEntity<List<PaymentPlanResponse>> listPlans(@AuthenticationPrincipal String principalUserId) {
        UUID userId = principalUserId == null || principalUserId.isBlank()
                ? null
                : parsePrincipalUserId(principalUserId);
        return ResponseEntity.ok(IPaymentUseCase.listPlans(userId));
    }

    @GetMapping("/subscription")
    @Operation(summary = "Get current subscription and token status")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription fetched",
                    content = @Content(schema = @Schema(implementation = PaymentSubscriptionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<PaymentSubscriptionResponse> getSubscription(
            @AuthenticationPrincipal String principalUserId
    ) {
        return ResponseEntity.ok(IPaymentUseCase.getSubscription(parsePrincipalUserId(principalUserId)));
    }

    @PostMapping("/checkout-sessions")
    @Operation(summary = "Create a Mercado Pago checkout link for a paid subscription")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Checkout session created",
                    content = @Content(schema = @Schema(implementation = CreateCheckoutSessionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<CreateCheckoutSessionResponse> createCheckoutSession(
            @AuthenticationPrincipal String principalUserId,
            @RequestBody(required = false) CreateCheckoutSessionRequest request
    ) {
        if (request == null) {
            throw new InvalidPaymentRequestException("Checkout request is required");
        }
        return ResponseEntity.ok(IPaymentUseCase.createCheckoutSession(parsePrincipalUserId(principalUserId), request));
    }

    @PostMapping("/subscription/cancel")
    @Operation(summary = "Cancel the current Mercado Pago subscription")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Subscription canceled"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> cancelSubscription(@AuthenticationPrincipal String principalUserId) {
        IPaymentUseCase.cancelSubscription(parsePrincipalUserId(principalUserId));
        return ResponseEntity.noContent().build();
    }

    private UUID parsePrincipalUserId(String principalUserId) {
        if (principalUserId == null || principalUserId.isBlank()) {
            throw new InvalidPaymentRequestException("Authenticated user is required");
        }
        try {
            return UUID.fromString(principalUserId.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidPaymentRequestException("Authenticated user id is invalid");
        }
    }
}
