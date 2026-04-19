package com.legalfam.backend.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.conversation.dto.ConversationAskResponse;
import com.legalfam.backend.conversation.n8n.N8nChatClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private N8nChatClient n8nChatClient;

    @Test
    void chatUsesN8n() {
        ConversationService conversationService = new ConversationService(n8nChatClient);
        ConversationAskResponse expected = new ConversationAskResponse("n8n answer", List.of());

        when(n8nChatClient.generateAnswer("hello")).thenReturn(expected);

        ConversationAskResponse response = conversationService.chat("hello");

        assertEquals(expected, response);
        verify(n8nChatClient).generateAnswer("hello");
    }
}

