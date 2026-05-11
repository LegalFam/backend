package com.legalfam.backend.chat.infrastructure.api.handler;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.chat.domain.exception.PendingAssistantMessageException;
import com.legalfam.backend.chat.domain.exception.ChatUpstreamException;
import com.legalfam.backend.chat.infrastructure.api.handler.ChatExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ChatExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingChatController())
                .setControllerAdvice(new ChatExceptionHandler())
                .build();
    }

    @Test
    void mapsChatUpstreamToBadGateway() throws Exception {
        mockMvc.perform(get("/api/v1/chat/errors/upstream"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type", is("upstream_error")))
                .andExpect(jsonPath("$.code", is("upstream_service_unavailable")))
                .andExpect(jsonPath("$.message", is("n8n service unavailable")))
                .andExpect(jsonPath("$.status", is(502)))
                .andExpect(jsonPath("$.path", is("/api/v1/chat/errors/upstream")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void mapsPendingAssistantMessageToConflict() throws Exception {
        mockMvc.perform(get("/api/v1/chat/errors/pending"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is("chat_state_error")))
                .andExpect(jsonPath("$.code", is("assistant_receipt_pending")))
                .andExpect(jsonPath("$.message", is("Assistant receipt confirmation is still pending for this session")))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.path", is("/api/v1/chat/errors/pending")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @RestController
    private static class ThrowingChatController {

        @GetMapping("/api/v1/chat/errors/upstream")
        String upstream() {
            throw new ChatUpstreamException("n8n service unavailable");
        }

        @GetMapping("/api/v1/chat/errors/pending")
        String pending() {
            throw new PendingAssistantMessageException("Assistant receipt confirmation is still pending for this session");
        }
    }
}
