package com.legalfam.backend.chat.infrastructure.adapter.persistence;

import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatSession;
import com.legalfam.backend.chat.infrastructure.persistence.ChatCitationRepository;
import com.legalfam.backend.chat.infrastructure.persistence.ChatMessageRepository;
import com.legalfam.backend.chat.infrastructure.persistence.ChatSessionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaChatPersistenceAdapter implements ChatPersistencePort {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatCitationRepository chatCitationRepository;

    public JpaChatPersistenceAdapter(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ChatCitationRepository chatCitationRepository
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatCitationRepository = chatCitationRepository;
    }

    @Override
    public Optional<ChatSession> findSessionById(UUID sessionId) {
        return chatSessionRepository.findById(sessionId).map(ChatEntityMapper::toDomain);
    }

    @Override
    public ChatSession saveSession(ChatSession chatSession) {
        return ChatEntityMapper.toDomain(chatSessionRepository.save(ChatEntityMapper.toEntity(chatSession)));
    }

    @Override
    public List<ChatSession> findSessionsByUserIdOrderByUpdatedAtDesc(UUID userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(ChatEntityMapper::toDomain)
                .toList();
    }

    @Override
    public ChatMessage saveMessage(ChatMessage chatMessage) {
        return ChatEntityMapper.toDomain(chatMessageRepository.save(ChatEntityMapper.toEntity(chatMessage)));
    }

    @Override
    public Optional<ChatMessage> findMessageById(UUID messageId) {
        return chatMessageRepository.findById(messageId).map(ChatEntityMapper::toDomain);
    }

    @Override
    public List<ChatMessage> findMessagesBySessionIdOrderByCreatedAtAsc(UUID sessionId) {
        return chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(ChatEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<ChatCitation> findCitationsByMessageIdsOrderByMessageIdAndId(List<UUID> messageIds) {
        return chatCitationRepository.findByChatMessageIdInOrderByChatMessageIdAscIdAsc(messageIds)
                .stream()
                .map(ChatEntityMapper::toDomain)
                .toList();
    }

    @Override
    public ChatCitation saveCitation(ChatCitation chatCitation) {
        return ChatEntityMapper.toDomain(chatCitationRepository.save(ChatEntityMapper.toEntity(chatCitation)));
    }
}
