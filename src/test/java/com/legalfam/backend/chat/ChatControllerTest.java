package com.legalfam.backend.chat;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.chat.dto.ChatAskResponse;
import com.legalfam.backend.chat.dto.ChatCitationResponse;
import com.legalfam.backend.error.handler.GlobalExceptionHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
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
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void askReturnsBadRequestWhenPromptIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("invalid_request")))
                .andExpect(jsonPath("$.message", is("Message is required")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/v1/chat")));

        verifyNoInteractions(chatService);
    }

    @Test
    void chatDelegatesToService() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(chatService.chat(any(UUID.class), anyString(), any(), any()))
                .thenReturn(new ChatAskResponse(sessionId, messageId, "Answer", List.of()));

        mockMvc.perform(post("/api/v1/chat")
                        .principal(() -> UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What does the contract say?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Answer")))
                .andExpect(jsonPath("$.sessionId", is(sessionId.toString())))
                .andExpect(jsonPath("$.messageId", is(messageId.toString())));

        verify(chatService).chat(any(UUID.class), anyString(), any(), any());
    }

    @Test
    void chatReturnsAnswerAndCitations() throws Exception {
        List<ChatCitationResponse> citations = List.of(
                new ChatCitationResponse("Contract.pdf", "Relevant excerpt", "https://example.com/doc-1")
        );
        when(chatService.chat(any(UUID.class), anyString(), any(), any()))
                .thenReturn(new ChatAskResponse(UUID.randomUUID(), UUID.randomUUID(), "Grounded answer", citations));

        mockMvc.perform(post("/api/v1/chat")
                        .principal(() -> UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What does the contract say?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Grounded answer")))
                .andExpect(jsonPath("$.citations[0].sourceTitle", is("Contract.pdf")))
                .andExpect(jsonPath("$.citations[0].sourceSnippet", is("Relevant excerpt")))
                .andExpect(jsonPath("$.citations[0].sourceUrl", is("https://example.com/doc-1")));
    }
}
