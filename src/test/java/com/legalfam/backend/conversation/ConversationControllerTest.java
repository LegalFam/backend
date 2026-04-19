package com.legalfam.backend.conversation;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.conversation.dto.ConversationAskResponse;
import com.legalfam.backend.conversation.dto.ConversationCitationResponse;
import com.legalfam.backend.error.handler.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

    @Mock
    private ConversationService conversationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void askReturnsBadRequestWhenPromptIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("invalid_request")))
                .andExpect(jsonPath("$.message", is("Prompt is required")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/v1/chat")));

        verifyNoInteractions(conversationService);
    }

    @Test
    void chatDelegatesToService() throws Exception {
        when(conversationService.chat(anyString()))
                .thenReturn(new ConversationAskResponse("Answer", List.of()));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"What does the contract say?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Answer")));

        verify(conversationService).chat("What does the contract say?");
    }

    @Test
    void chatReturnsAnswerAndCitations() throws Exception {
        List<ConversationCitationResponse> citations = List.of(
                new ConversationCitationResponse("files/demo-1", "Contract.pdf", "Relevant excerpt")
        );
        when(conversationService.chat(anyString()))
                .thenReturn(new ConversationAskResponse("Grounded answer", citations));

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"What does the contract say?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Grounded answer")))
                .andExpect(jsonPath("$.citations[0].fileId", is("files/demo-1")))
                .andExpect(jsonPath("$.citations[0].fileName", is("Contract.pdf")))
                .andExpect(jsonPath("$.citations[0].snippet", is("Relevant excerpt")));
    }
}
