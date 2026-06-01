package com.legalfam.backend.payment.infrastructure.api;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.common.error.handler.GlobalExceptionHandler;
import com.legalfam.backend.payment.application.dto.CreateCheckoutSessionResponse;
import com.legalfam.backend.payment.application.dto.PaymentPlanResponse;
import com.legalfam.backend.payment.application.dto.PaymentSubscriptionResponse;
import com.legalfam.backend.payment.application.service.PaymentService;
import com.legalfam.backend.payment.domain.exception.InsufficientTokensException;
import com.legalfam.backend.payment.domain.exception.InvalidPaymentRequestException;
import com.legalfam.backend.payment.infrastructure.api.handler.PaymentExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PaymentController(paymentService),
                        new PaymentWebhookController(paymentService)
                )
                .setControllerAdvice(new PaymentExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listPlansReturnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId.toString());
        when(paymentService.listPlans(userId)).thenReturn(List.of(
                new PaymentPlanResponse("FREE", "Free", "Starter access", "month", null, "pen", 50, true, true),
                new PaymentPlanResponse("BASIC", "Basic", "Paid access", "month", 1499, "pen", 500, false, true)
        ));

        mockMvc.perform(get("/api/v1/payments/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code", is("FREE")))
                .andExpect(jsonPath("$[0].monthlyTokenLimit", is(50)))
                .andExpect(jsonPath("$[1].monthlyPriceCents", is(1499)));
    }

    @Test
    void getSubscriptionReturnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId.toString());
        when(paymentService.getSubscription(userId)).thenReturn(new PaymentSubscriptionResponse(
                "FREE",
                "ACTIVE",
                "FREE",
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"),
                false,
                50,
                49
        ));

        mockMvc.perform(get("/api/v1/payments/subscription"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode", is("FREE")))
                .andExpect(jsonPath("$.remainingTokens", is(49)));
    }

    @Test
    void createCheckoutSessionReturnsBadRequestWhenPrincipalIsInvalid() throws Exception {
        authenticateAs("not-a-uuid");

        mockMvc.perform(post("/api/v1/payments/checkout-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"BASIC\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("invalid_request")))
                .andExpect(jsonPath("$.message", is("Authenticated user id is invalid")));

        verifyNoInteractions(paymentService);
    }

    @Test
    void createCheckoutSessionReturnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId.toString());
        when(paymentService.createCheckoutSession(eq(userId), any()))
                .thenReturn(new CreateCheckoutSessionResponse("https://www.mercadopago.com.pe/subscriptions/checkout"));

        mockMvc.perform(post("/api/v1/payments/checkout-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"BASIC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", is("https://www.mercadopago.com.pe/subscriptions/checkout")));
    }

    @Test
    void cancelSubscriptionReturnsNoContent() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId.toString());

        mockMvc.perform(post("/api/v1/payments/subscription/cancel"))
                .andExpect(status().isNoContent());

        verify(paymentService).cancelSubscription(userId);
    }

    @Test
    void cancelSubscriptionReturnsForbiddenWhenTokensAreMissing() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId.toString());
        doThrow(new InsufficientTokensException("No chat tokens remaining for the current period"))
                .when(paymentService)
                .cancelSubscription(userId);

        mockMvc.perform(post("/api/v1/payments/subscription/cancel"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("insufficient_tokens")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void webhookReturnsBadRequestWhenPayloadIsMissing() throws Exception {
        doThrow(new InvalidPaymentRequestException("Webhook payload is required"))
                .when(paymentService)
                .handleWebhook(any(), any());

        mockMvc.perform(post("/api/v1/payments/webhook/mercado-pago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void webhookReturnsOk() throws Exception {
        mockMvc.perform(post("/api/v1/payments/webhook/mercado-pago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "ts=1,v1=test")
                        .content("{\"id\":\"evt_123\"}"))
                .andExpect(status().isOk());

        verify(paymentService).handleWebhook("{\"id\":\"evt_123\"}", "ts=1,v1=test");
    }

    private void authenticateAs(String principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null)
        );
    }
}
