package com.legalfam.backend.chat.application.mapper;

import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import com.legalfam.backend.chat.application.dto.ChatMessageResponse;
import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatOutboxEvent;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageResponseMapper {

    public ChatMessageResponse toResponse(
            ChatMessage message,
            Map<UUID, List<ChatCitation>> citationsByMessageId,
            Map<UUID, ChatOutboxEvent> outboxByMessageId
    ) {
        ChatOutboxEvent outboxEvent = outboxByMessageId.get(message.getId());
        return new ChatMessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getErrorCode(),
                message.getRating(),
                message.getFeedbackComment(),
                message.getFeedbackSubmittedAt(),
                message.getCreatedAt(),
                mapCitations(citationsByMessageId.getOrDefault(message.getId(), Collections.emptyList())),
                message.getConfidenceStatus(),
                message.getConfidenceReason(),
                message.getNextSteps(),
                message.getSpecialistSupportRecommended(),
                message.getCitationSupportStatus(),
                resolveReceiptStatus(message, outboxEvent),
                resolveReadAt(message, outboxEvent)
        );
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

    private String resolveReceiptStatus(ChatMessage message, ChatOutboxEvent event) {
        if (message.getRole() != ChatMessageRole.ASSISTANT || event == null) {
            return null;
        }
        return event.getStatus().name();
    }

    private Instant resolveReadAt(ChatMessage message, ChatOutboxEvent event) {
        if (message.getRole() != ChatMessageRole.ASSISTANT || event == null) {
            return null;
        }
        return event.getReadAt();
    }
}
