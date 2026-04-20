package com.legalfam.backend.chat;

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

import com.legalfam.backend.chat.dto.ChatAskResponse;
import com.legalfam.backend.chat.dto.ChatCitationResponse;
import com.legalfam.backend.chat.dto.ChatMessageResponse;
import com.legalfam.backend.chat.dto.ChatSessionResponse;
import com.legalfam.backend.chat.exception.handler.ChatExceptionHandler;
import com.legalfam.backend.error.handler.GlobalExceptionHandler;
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
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService))
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

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("invalid_request")))
                .andExpect(jsonPath("$.message", is("Message is required")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/v1/chat")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verifyNoInteractions(chatService);
    }

    @Test
    void chatReturnsOkWhenPayloadIsValid() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        authenticateAs(userId.toString());

        when(chatService.chat(eq(userId), eq("hola"), eq(sessionId)))
                .thenReturn(new ChatAskResponse(
                        sessionId,
                        messageId,
                        "Respuesta",
                        List.of(new ChatCitationResponse("Codigo Penal", "Art. 1", "https://example.com/doc"))
                ));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  hola  \",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId", is(sessionId.toString())))
                .andExpect(jsonPath("$.messageId", is(messageId.toString())))
                .andExpect(jsonPath("$.message", is("Respuesta")))
                .andExpect(jsonPath("$.citations[0].sourceTitle", is("Codigo Penal")))
                .andExpect(jsonPath("$.citations[0].sourceSnippet", is("Art. 1")))
                .andExpect(jsonPath("$.citations[0].sourceUrl", is("https://example.com/doc")));
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

        verifyNoInteractions(chatService);
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
