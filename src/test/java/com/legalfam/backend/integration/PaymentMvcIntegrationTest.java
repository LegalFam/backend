package com.legalfam.backend.integration;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.auth.application.port.out.ITokenValidationPort;
import com.legalfam.backend.auth.infrastructure.config.CorsProperties;
import com.legalfam.backend.auth.infrastructure.security.JwtAuthenticationFilter;
import com.legalfam.backend.auth.infrastructure.security.SecurityConfig;
import com.legalfam.backend.common.error.handler.GlobalExceptionHandler;
import com.legalfam.backend.common.security.AuthenticatedUserResolver;
import com.legalfam.backend.payment.application.dto.PaymentPlanResponse;
import com.legalfam.backend.payment.application.dto.PaymentSubscriptionResponse;
import com.legalfam.backend.payment.application.port.in.IPaymentUseCase;
import com.legalfam.backend.payment.infrastructure.api.PaymentController;
import com.legalfam.backend.payment.infrastructure.api.PaymentWebhookController;
import com.legalfam.backend.payment.infrastructure.api.handler.PaymentExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
        PaymentController.class,
        PaymentWebhookController.class
})
@Import({
        AuthenticatedUserResolver.class,
        JwtAuthenticationFilter.class,
        SecurityConfig.class,
        PaymentExceptionHandler.class,
        GlobalExceptionHandler.class
})
@EnableConfigurationProperties(CorsProperties.class)
class PaymentMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPaymentUseCase paymentUseCase;

    @MockitoBean
    private ITokenValidationPort tokenValidationPort;

    @Test
    void plansEndpointIsPublicEvenWhenNoBearerTokenIsProvided() throws Exception {
        when(paymentUseCase.listPlans(null)).thenReturn(List.of(
                new PaymentPlanResponse("FREE", "Free", "Starter access", "month", null, "pen", 50, true, true),
                new PaymentPlanResponse("BASIC", "Basic", "Paid access", "month", 1499, "pen", 500, false, true)
        ));

        mockMvc.perform(get("/api/v1/payments/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code", is("FREE")))
                .andExpect(jsonPath("$[0].monthlyTokenLimit", is(50)))
                .andExpect(jsonPath("$[1].code", is("BASIC")))
                .andExpect(jsonPath("$[1].monthlyPriceCents", is(1499)));

        verify(paymentUseCase).listPlans(null);
    }

    @Test
    void plansEndpointUsesAuthenticatedUserWhenBearerTokenIsValid() throws Exception {
        UUID userId = UUID.randomUUID();
        when(tokenValidationPort.isTokenValid("valid-token")).thenReturn(true);
        when(tokenValidationPort.extractUserId("valid-token")).thenReturn(userId);
        when(paymentUseCase.listPlans(userId)).thenReturn(List.of(
                new PaymentPlanResponse("FREE", "Free", "Starter access", "month", null, "pen", 50, true, true)
        ));

        mockMvc.perform(get("/api/v1/payments/plans")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code", is("FREE")))
                .andExpect(jsonPath("$[0].currentPlan", is(true)));

        verify(paymentUseCase).listPlans(userId);
    }

    @Test
    void paymentWebhookIsPublicAndForwardsHeadersQueryAndBody() throws Exception {
        mockMvc.perform(post("/api/v1/payments/webhook/mercado-pago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "ts=1,v1=test")
                        .header("X-Request-Id", "request-123")
                        .queryParam("data.id", " event-123 ")
                        .content("{\"id\":\"event-123\"}"))
                .andExpect(status().isOk());

        verify(paymentUseCase).handleWebhook("{\"id\":\"event-123\"}", "ts=1,v1=test", "request-123", "event-123");
    }

    @Test
    void protectedPaymentEndpointAcceptsValidBearerTokenAndUsesAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(tokenValidationPort.isTokenValid("valid-token")).thenReturn(true);
        when(tokenValidationPort.extractUserId("valid-token")).thenReturn(userId);
        when(paymentUseCase.getSubscription(userId)).thenReturn(new PaymentSubscriptionResponse(
                "FREE",
                "ACTIVE",
                "FREE",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                false,
                50,
                49
        ));

        mockMvc.perform(get("/api/v1/payments/subscription")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planCode", is("FREE")))
                .andExpect(jsonPath("$.remainingTokens", is(49)));

        verify(paymentUseCase).getSubscription(userId);
    }

    @Test
    void checkoutValidationRunsThroughRealMvcAdvice() throws Exception {
        UUID userId = UUID.randomUUID();
        when(tokenValidationPort.isTokenValid("valid-token")).thenReturn(true);
        when(tokenValidationPort.extractUserId("valid-token")).thenReturn(userId);

        mockMvc.perform(post("/api/v1/payments/checkout-sessions")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"\",\"successUrl\":\"ftp://example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("plan_code_required")))
                .andExpect(jsonPath("$.path", is("/api/v1/payments/checkout-sessions")));

        verifyNoInteractions(paymentUseCase);
    }
}
