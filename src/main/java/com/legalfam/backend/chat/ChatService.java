package com.legalfam.backend.chat;

import com.legalfam.backend.chat.dto.ChatAskResponse;
import com.legalfam.backend.chat.dto.ChatCitationResponse;
import com.legalfam.backend.chat.exception.ChatUpstreamException;
import com.legalfam.backend.chat.integration.N8nWebhookClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatService {

    private final N8nWebhookClient n8nWebhookClient;

    public ChatService(N8nWebhookClient n8nWebhookClient) {
        this.n8nWebhookClient = n8nWebhookClient;
    }

    public ChatAskResponse chat(String prompt) {
        JsonNode root = n8nWebhookClient.sendPrompt(prompt);

        String message = readText(root, "message");
        if (isBlank(message)) {
            throw new ChatUpstreamException("n8n response does not include a message");
        }

        List<ChatCitationResponse> citations = extractCitations(root.get("citations"));

        return new ChatAskResponse(message, citations);
    }

    private List<ChatCitationResponse> extractCitations(JsonNode citationsNode) {
        if (citationsNode == null || citationsNode.isNull()) {
            return List.of();
        }
        if (citationsNode.isArray()) {
            List<ChatCitationResponse> citations = new ArrayList<>();
            for (JsonNode citationNode : citationsNode) {
                ChatCitationResponse citation = mapCitation(citationNode);
                if (citation != null) {
                    citations.add(citation);
                }
            }
            return citations;
        }
        if (citationsNode.isObject()) {
            ChatCitationResponse singleCitation = mapCitation(citationsNode);
            if (singleCitation != null) {
                return List.of(singleCitation);
            }
        }
        return List.of();
    }

    private ChatCitationResponse mapCitation(JsonNode citationNode) {
        if (citationNode == null || citationNode.isNull()) {
            return null;
        }
        String fileId = readText(citationNode, "file_id");
        String fileName = readText(citationNode, "file_name");
        String snippet = readText(citationNode, "snippet");

        if (isBlank(fileId) && isBlank(fileName) && isBlank(snippet)) {
            return null;
        }
        return new ChatCitationResponse(fileId, fileName, snippet);
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
