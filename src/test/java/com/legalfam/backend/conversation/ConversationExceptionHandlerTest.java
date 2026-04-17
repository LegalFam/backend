package com.legalfam.backend.conversation;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.conversation.exception.ConversationUpstreamException;
import com.legalfam.backend.conversation.exception.handler.ConversationExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ConversationExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingConversationController())
                .setControllerAdvice(new ConversationExceptionHandler())
                .build();
    }

    @Test
    void mapsConversationUpstreamToBadGateway() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/errors/upstream"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type", is("upstream_error")))
                .andExpect(jsonPath("$.code", is("upstream_service_unavailable")))
                .andExpect(jsonPath("$.message", is("Gemini service unavailable")))
                .andExpect(jsonPath("$.status", is(502)))
                .andExpect(jsonPath("$.path", is("/api/v1/conversations/errors/upstream")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @RestController
    private static class ThrowingConversationController {

        @GetMapping("/api/v1/conversations/errors/upstream")
        String upstream() {
            throw new ConversationUpstreamException("Gemini service unavailable");
        }
    }
}
