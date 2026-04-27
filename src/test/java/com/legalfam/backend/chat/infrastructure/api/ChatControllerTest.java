package com.legalfam.backend.chat.infrastructure.api;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import com.legalfam.backend.chat.application.dto.ChatMessageResponse;
import com.legalfam.backend.chat.application.dto.ChatSendAcceptedResponse;
import com.legalfam.backend.chat.application.dto.ChatSessionResponse;
import com.legalfam.backend.chat.infrastructure.api.handler.ChatExceptionHandler;
import com.legalfam.backend.chat.application.service.ChatService;
import com.legalfam.backend.chat.infrastructure.sse.ChatSseEmitterService;
import com.legalfam.backend.common.error.handler.GlobalExceptionHandler;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private ChatSseEmitterService chatSseEmitterService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService, chatSseEmitterService))
                .setControllerAdvice(new ChatExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chatReturnsBadRequestWhenMessageIsBlank() throws Exception {
        authenticateAs(UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("invalid_request")))
                .andExpect(jsonPath("$.message", is("Message is required")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/v1/chat/send")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verifyNoInteractions(chatService, chatSseEmitterService);
    }

    @Test
    void sendReturnsBadRequestWhenSessionIdIsMissing() throws Exception {
        authenticateAs(UUID.randomUUID().toString());

        mockMvc.perform(post("/api/v1/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hola\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("invalid_request")))
                .andExpect(jsonPath("$.message", is("Session id is required")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/v1/chat/send")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verifyNoInteractions(chatService, chatSseEmitterService);
    }

    @Test
    void sendReturnsAcceptedWhenPayloadIsValid() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        authenticateAs(userId.toString());

        when(chatService.send(eq(userId), eq("hola"), eq(sessionId)))
                .thenReturn(new ChatSendAcceptedResponse(
                        sessionId,
                        userMessageId,
                        "PROCESSING"
                ));

        mockMvc.perform(post("/api/v1/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  hola  \",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sessionId", is(sessionId.toString())))
                .andExpect(jsonPath("$.userMessageId", is(userMessageId.toString())))
                .andExpect(jsonPath("$.status", is("PROCESSING")));
    }

    @Test
    void subscribeReturnsOkForOwnedSession() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        authenticateAs(userId.toString());
        SseEmitter emitter = new SseEmitter(60000L);

        when(chatSseEmitterService.subscribe(userId, sessionId)).thenReturn(emitter);

        mockMvc.perform(get("/api/v1/chat/subscribe/{sessionId}", sessionId))
                .andExpect(status().isOk());

        verify(chatService).assertSessionOwnershipExists(userId, sessionId);
        verify(chatSseEmitterService).subscribe(userId, sessionId);
    }

    @Test
    void createSessionReturnsCreated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        authenticateAs(userId.toString());

        when(chatService.createSession(userId))
                .thenReturn(new ChatSessionResponse(
                        sessionId,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z")
                ));

        mockMvc.perform(post("/api/v1/chat/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(sessionId.toString())));
    }

    @Test
    void listSessionsReturnsBadRequestWhenPrincipalUserIdIsInvalid() throws Exception {
        authenticateAs("not-a-uuid");

        mockMvc.perform(get("/api/v1/chat/sessions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("invalid_request")))
                .andExpect(jsonPath("$.message", is("Authenticated user id is invalid")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/v1/chat/sessions")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verifyNoInteractions(chatService, chatSseEmitterService);
    }

    @Test
    void listSessionsReturnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        authenticateAs(userId.toString());

        when(chatService.listSessions(userId))
                .thenReturn(List.of(new ChatSessionResponse(
                        sessionId,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:10:00Z")
                )));

        mockMvc.perform(get("/api/v1/chat/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(sessionId.toString())));
    }

    @Test
    void listMessagesReturnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        authenticateAs(userId.toString());

        when(chatService.listMessages(userId, sessionId))
                .thenReturn(List.of(new ChatMessageResponse(
                        messageId,
                        "ASSISTANT",
                        "Hola",
                        5,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        List.of(new ChatCitationResponse("Ley", "Texto", "https://example.com/ley"))
                )));

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(messageId.toString())))
                .andExpect(jsonPath("$[0].role", is("ASSISTANT")))
                .andExpect(jsonPath("$[0].content", is("Hola")))
                .andExpect(jsonPath("$[0].rating", is(5)))
                .andExpect(jsonPath("$[0].citations[0].sourceUrl", is("https://example.com/ley")));
    }

    @Test
    void rateMessageReturnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        authenticateAs(userId.toString());

        mockMvc.perform(patch("/api/v1/chat/messages/{messageId}/rating", messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isOk());

        verify(chatService).rateMessage(eq(userId), eq(messageId), any());
    }

    private void authenticateAs(String principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null)
        );
    }
}
