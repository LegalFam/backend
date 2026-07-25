package com.legalfam.backend.integration;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.auth.application.port.out.ITokenValidationPort;
import com.legalfam.backend.auth.infrastructure.config.CorsProperties;
import com.legalfam.backend.auth.infrastructure.security.JwtAuthenticationFilter;
import com.legalfam.backend.auth.infrastructure.security.SecurityConfig;
import com.legalfam.backend.chat.application.dto.ChatSessionResponse;
import com.legalfam.backend.chat.application.port.in.IChatUseCase;
import com.legalfam.backend.chat.domain.exception.InsufficientChatTokensException;
import com.legalfam.backend.chat.infrastructure.adapter.out.SseChatAssistantDeliveryAdapter;
import com.legalfam.backend.chat.infrastructure.api.ChatController;
import com.legalfam.backend.chat.infrastructure.api.handler.ChatExceptionHandler;
import com.legalfam.backend.common.error.handler.GlobalExceptionHandler;
import com.legalfam.backend.common.security.AuthenticatedUserResolver;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ChatController.class)
@Import({
        AuthenticatedUserResolver.class,
        JwtAuthenticationFilter.class,
        SecurityConfig.class,
        ChatExceptionHandler.class,
        GlobalExceptionHandler.class
})
@EnableConfigurationProperties(CorsProperties.class)
class ChatMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IChatUseCase chatUseCase;

    @MockitoBean
    private ITokenValidationPort tokenValidationPort;

    @MockitoBean
    private SseChatAssistantDeliveryAdapter sseChatAssistantDeliveryAdapter;

    @Test
    void protectedEndpointReturnsJsonUnauthorizedWhenBearerTokenIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/chat/sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type", is("authentication_error")))
                .andExpect(jsonPath("$.code", is("unauthorized")))
                .andExpect(jsonPath("$.message", is("Authentication is required")))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.path", is("/api/v1/chat/sessions")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verifyNoInteractions(chatUseCase, sseChatAssistantDeliveryAdapter);
    }

    @Test
    void protectedEndpointReturnsJsonUnauthorizedWhenBearerTokenIsInvalid() throws Exception {
        when(tokenValidationPort.isTokenValid("invalid-token")).thenReturn(false);

        mockMvc.perform(post("/api/v1/chat/sessions")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type", is("authentication_error")))
                .andExpect(jsonPath("$.code", is("unauthorized")))
                .andExpect(jsonPath("$.message", is("Authentication is required")))
                .andExpect(jsonPath("$.path", is("/api/v1/chat/sessions")));

        verify(tokenValidationPort).isTokenValid("invalid-token");
        verify(tokenValidationPort, never()).extractUserId("invalid-token");
        verifyNoInteractions(chatUseCase, sseChatAssistantDeliveryAdapter);
    }

    @Test
    void protectedEndpointAcceptsValidBearerTokenAndUsesAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(tokenValidationPort.isTokenValid("valid-token")).thenReturn(true);
        when(tokenValidationPort.extractUserId("valid-token")).thenReturn(userId);
        when(chatUseCase.createSession(eq(userId)))
                .thenReturn(new ChatSessionResponse(
                        sessionId,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z")
                ));

        mockMvc.perform(post("/api/v1/chat/sessions")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(sessionId.toString())));

        verify(chatUseCase).createSession(userId);
    }

    @Test
    void chatSendValidationRunsThroughRealMvcAdvice() throws Exception {
        UUID userId = UUID.randomUUID();
        when(tokenValidationPort.isTokenValid("valid-token")).thenReturn(true);
        when(tokenValidationPort.extractUserId("valid-token")).thenReturn(userId);

        mockMvc.perform(post("/api/v1/chat/send")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hola\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("session_id_required")))
                .andExpect(jsonPath("$.path", is("/api/v1/chat/send")));

        verifyNoInteractions(chatUseCase);
    }

    @Test
    void chatSendReturnsForbiddenInsufficientTokensThroughRealMvcAdvice() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(tokenValidationPort.isTokenValid("valid-token")).thenReturn(true);
        when(tokenValidationPort.extractUserId("valid-token")).thenReturn(userId);
        when(chatUseCase.send(eq(userId), eq("hola"), eq(sessionId)))
                .thenThrow(InsufficientChatTokensException.noTokens());

        mockMvc.perform(post("/api/v1/chat/send")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hola\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", is("payment_error")))
                .andExpect(jsonPath("$.code", is("insufficient_tokens")))
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.path", is("/api/v1/chat/send")));

        verify(chatUseCase).send(userId, "hola", sessionId);
    }

    @Test
    void chatRatingValidationRunsThroughRealMvcAdvice() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(tokenValidationPort.isTokenValid("valid-token")).thenReturn(true);
        when(tokenValidationPort.extractUserId("valid-token")).thenReturn(userId);

        mockMvc.perform(patch("/api/v1/chat/messages/{messageId}/rating", messageId)
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("rating_out_of_range")))
                .andExpect(jsonPath("$.path", is("/api/v1/chat/messages/" + messageId + "/rating")));

        verifyNoInteractions(chatUseCase);
    }
}
