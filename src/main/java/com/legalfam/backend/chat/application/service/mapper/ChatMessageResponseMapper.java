package com.legalfam.backend.chat.application.service.mapper;

import com.legalfam.backend.chat.application.dto.ChatCitationResponse;
import com.legalfam.backend.chat.application.dto.ChatMessageResponse;
import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
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
                message.getLanguage().code(),
                message.getLanguageRequested() == null ? null : message.getLanguageRequested().code(),
                message.getContentLocalized(),
                message.getErrorCode(),
                message.getRating(),
                message.getFeedbackComment(),
                message.getFeedbackSubmittedAt(),
                message.getCreatedAt(),
                mapCitations(citationsByMessageId.getOrDefault(message.getId(), Collections.emptyList())),
                message.getConfidenceStatus(),
                message.getConfidenceReason(),
                message.getNextSteps(),
                message.getNextStepsLocalized(),
                message.getSpecialistSupportRecommended(),
                message.getCitationSupportStatus(),
                resolveReceiptStatus(outboxEvent),
                resolveReadAt(outboxEvent)
        );
    }

    private List<ChatCitationResponse> mapCitations(List<ChatCitation> citations) {
        return citations.stream()
                .map(citation -> new ChatCitationResponse(
                        citation.getSourceTitle(),
                        citation.getSourceSnippet(),
                        citation.getSourceSnippetLocalized(),
                        citation.getSourceOriginalSnippet(),
                        citation.getSourceUrl(),
                        citation.getSourceLocator(),
                        citation.getSourceBreadcrumb(),
                        citation.getSourceLocatorKind()
                ))
                .toList();
    }

    private String resolveReceiptStatus(ChatOutboxEvent event) {
        return event == null ? null : event.getStatus().name();
    }

    private Instant resolveReadAt(ChatOutboxEvent event) {
        return event == null ? null : event.getReadAt();
    }
}
