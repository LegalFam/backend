package com.legalfam.backend.conversation;

import com.legalfam.backend.conversation.dto.ConversationAskResponse;
import com.legalfam.backend.conversation.gemini.GeminiFileSearchClient;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {

    private final GeminiFileSearchClient geminiFileSearchClient;

    public ConversationService(GeminiFileSearchClient geminiFileSearchClient) {
        this.geminiFileSearchClient = geminiFileSearchClient;
    }

    public ConversationAskResponse chatWithFileSearch(String prompt, String fileSearchStoreName) {
        return geminiFileSearchClient.generateAnswer(prompt, fileSearchStoreName);
    }
}
