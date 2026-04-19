package com.legalfam.backend.conversation;

import com.legalfam.backend.conversation.dto.ConversationAskResponse;
import com.legalfam.backend.conversation.n8n.N8nChatClient;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final N8nChatClient n8nChatClient;

    public ConversationService(N8nChatClient n8nChatClient) {
        this.n8nChatClient = n8nChatClient;
    }

    public ConversationAskResponse chat(String prompt) {
        return n8nChatClient.generateAnswer(prompt);
    }
}
