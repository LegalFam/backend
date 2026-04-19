package com.legalfam.backend.conversation;

import com.legalfam.backend.conversation.dto.ConversationAskResponse;
import com.legalfam.backend.conversation.dto.ConversationCitationResponse;
import com.legalfam.backend.conversation.exception.ConversationUpstreamException;
import com.legalfam.backend.conversation.integration.N8nWebhookClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationService {

    private final N8nWebhookClient n8nWebhookClient;

    public ConversationService(N8nWebhookClient n8nWebhookClient) {
        this.n8nWebhookClient = n8nWebhookClient;
    }

    public ConversationAskResponse chat(String prompt) {
        JsonNode root = n8nWebhookClient.sendPrompt(prompt);

        String message = readText(root, "message");
        if (isBlank(message)) {
            throw new ConversationUpstreamException("n8n response does not include a message");
        }

        List<ConversationCitationResponse> citations = extractCitations(root.get("citations"));

        return new ConversationAskResponse(message, citations);
    }

    private List<ConversationCitationResponse> extractCitations(JsonNode citationsNode) {
        if (citationsNode == null || citationsNode.isNull()) {
            return List.of();
        }
        if (citationsNode.isArray()) {
            List<ConversationCitationResponse> citations = new ArrayList<>();
            for (JsonNode citationNode : citationsNode) {
                ConversationCitationResponse citation = mapCitation(citationNode);
                if (citation != null) {
                    citations.add(citation);
                }
            }
            return citations;
        }
        if (citationsNode.isObject()) {
            ConversationCitationResponse singleCitation = mapCitation(citationsNode);
            if (singleCitation != null) {
                return List.of(singleCitation);
            }
        }
        return List.of();
    }

    private ConversationCitationResponse mapCitation(JsonNode citationNode) {
        if (citationNode == null || citationNode.isNull()) {
            return null;
        }
        String fileId = readText(citationNode, "file_id");
        String fileName = readText(citationNode, "file_name");
        String snippet = readText(citationNode, "snippet");

        if (isBlank(fileId) && isBlank(fileName) && isBlank(snippet)) {
            return null;
        }
        return new ConversationCitationResponse(fileId, fileName, snippet);
    }

    private String readText(JsonNode node, String key) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode child = node.get(key);
        if (child == null || child.isNull()) {
            return null;
        }
        String text = child.isTextual() ? child.asText() : child.toString();
        return isBlank(text) ? null : text.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
