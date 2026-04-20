package com.legalfam.backend.chat;

import com.legalfam.backend.chat.dto.ChatMessageResponse;
import com.legalfam.backend.chat.dto.ChatRateMessageRequest;
import com.legalfam.backend.chat.dto.ChatAskResponse;
import com.legalfam.backend.chat.dto.ChatCitationResponse;
import com.legalfam.backend.chat.dto.ChatSessionResponse;
import com.legalfam.backend.chat.exception.ChatAccessDeniedException;
import com.legalfam.backend.chat.exception.ChatNotFoundException;
import com.legalfam.backend.chat.exception.ChatUpstreamException;
import com.legalfam.backend.chat.integration.N8nWebhookClient;
import com.legalfam.backend.chat.model.ChatCitation;
import com.legalfam.backend.chat.model.ChatMessage;
import com.legalfam.backend.chat.model.ChatMessageRole;
import com.legalfam.backend.chat.model.ChatSession;
import com.legalfam.backend.error.exception.InvalidRequestException;
import com.legalfam.backend.user.User;
import com.legalfam.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private final N8nWebhookClient n8nWebhookClient;
    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatCitationRepository chatCitationRepository;

    public ChatService(
            N8nWebhookClient n8nWebhookClient,
            UserRepository userRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ChatCitationRepository chatCitationRepository
    ) {
        this.n8nWebhookClient = n8nWebhookClient;
        this.userRepository = userRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatCitationRepository = chatCitationRepository;
    }

    @Transactional
    public ChatAskResponse chat(UUID userId, String messageInput, UUID sessionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ChatAccessDeniedException("Authenticated user not found"));
        ChatSession chatSession = resolveSession(user, sessionId);
        Instant now = Instant.now();

        ChatMessage userMessage = new ChatMessage();
        userMessage.setChatSession(chatSession);
        userMessage.setRole(ChatMessageRole.USER);
        userMessage.setContent(messageInput);
        userMessage.setCreatedAt(now);
        chatMessageRepository.save(userMessage);

        JsonNode root = n8nWebhookClient.sendMessage(messageInput, chatSession.getId());

        String message = readText(root, "message");
        if (isBlank(message)) {
            throw new ChatUpstreamException("n8n response does not include a message");
        }

        List<ChatCitationResponse> citations = extractCitations(root.get("citations"));

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setChatSession(chatSession);
        assistantMessage.setRole(ChatMessageRole.ASSISTANT);
        assistantMessage.setContent(message);
        assistantMessage.setCreatedAt(Instant.now());
        assistantMessage = chatMessageRepository.save(assistantMessage);

        persistCitations(assistantMessage, citations);

        chatSession.setUpdatedAt(Instant.now());
        chatSessionRepository.save(chatSession);

        return new ChatAskResponse(chatSession.getId(), assistantMessage.getId(), message, citations);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(UUID userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(session -> new ChatSessionResponse(
                        session.getId(),
                        session.getCreatedAt(),
                        session.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(UUID userId, UUID sessionId) {
        ChatSession session = loadOwnedSession(userId, sessionId);

        return chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(message -> new ChatMessageResponse(
                        message.getId(),
                        message.getRole().name(),
                        message.getContent(),
                        message.getRating(),
                        message.getCreatedAt(),
                        mapCitations(chatCitationRepository.findByChatMessageIdOrderByIdAsc(message.getId()))
                ))
                .toList();
    }

    @Transactional
    public void rateMessage(UUID userId, UUID messageId, ChatRateMessageRequest request) {
        if (request == null || request.rating() == null) {
            throw new InvalidRequestException("Rating is required");
        }
        if (request.rating() < 1 || request.rating() > 5) {
            throw new InvalidRequestException("Rating must be between 1 and 5");
        }

        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ChatNotFoundException("Chat message not found"));

        UUID ownerId = message.getChatSession().getUser().getId();
        if (!ownerId.equals(userId)) {
            throw new ChatAccessDeniedException("Access is forbidden");
        }

        message.setRating(request.rating());
        chatMessageRepository.save(message);
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

        String sourceTitle = readText(citationNode, "file_name");
        String sourceSnippet = readText(citationNode, "snippet");
        String sourceUrl = readText(citationNode, "file_url");

        if (isBlank(sourceTitle) && isBlank(sourceSnippet) && isBlank(sourceUrl)) {
            return null;
        }
        if (isBlank(sourceUrl)) {
            throw new ChatUpstreamException("n8n citation is missing file_url");
        }
        return new ChatCitationResponse(sourceTitle, sourceSnippet, sourceUrl);
    }

    private ChatSession resolveSession(User user, UUID sessionId) {
        if (sessionId != null) {
            return loadOwnedSession(user.getId(), sessionId);
        }

        Instant now = Instant.now();
        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return chatSessionRepository.save(session);
    }

    private ChatSession loadOwnedSession(UUID userId, UUID sessionId) {
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ChatNotFoundException("Chat session not found"));

        if (!session.getUser().getId().equals(userId)) {
            throw new ChatAccessDeniedException("Access is forbidden");
        }
        return session;
    }

    private void persistCitations(ChatMessage assistantMessage, List<ChatCitationResponse> citations) {
        for (ChatCitationResponse citation : citations) {
            ChatCitation entity = new ChatCitation();
            entity.setChatMessage(assistantMessage);
            entity.setSourceTitle(citation.sourceTitle());
            entity.setSourceSnippet(citation.sourceSnippet());
            entity.setSourceUrl(citation.sourceUrl());
            chatCitationRepository.save(entity);
        }
    }

    private List<ChatCitationResponse> mapCitations(List<ChatCitation> citations) {
        return citations.stream()
                .map(citation -> new ChatCitationResponse(
                        citation.getSourceTitle(),
                        citation.getSourceSnippet(),
                        citation.getSourceUrl()
                ))
                .toList();
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
