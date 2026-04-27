package com.legalfam.backend.chat.infrastructure.adapter.persistence;

import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatCitation;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatSession;
import com.legalfam.backend.chat.infrastructure.persistence.ChatCitationRepository;
import com.legalfam.backend.chat.infrastructure.persistence.ChatMessageRepository;
import com.legalfam.backend.chat.infrastructure.persistence.ChatSessionRepository;
import com.legalfam.backend.user.domain.model.User;
import com.legalfam.backend.user.infrastructure.persistence.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JpaChatPersistenceAdapter implements ChatPersistencePort {

    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatCitationRepository chatCitationRepository;

    public JpaChatPersistenceAdapter(
            UserRepository userRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ChatCitationRepository chatCitationRepository
    ) {
        this.userRepository = userRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatCitationRepository = chatCitationRepository;
    }

    @Override
    public Optional<User> findUserById(UUID userId) {
        return userRepository.findById(userId);
    }

    @Override
    public Optional<ChatSession> findSessionById(UUID sessionId) {
        return chatSessionRepository.findById(sessionId);
    }

    @Override
    public ChatSession saveSession(ChatSession chatSession) {
        return chatSessionRepository.save(chatSession);
    }

    @Override
    public List<ChatSession> findSessionsByUserIdOrderByUpdatedAtDesc(UUID userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Override
    public ChatMessage saveMessage(ChatMessage chatMessage) {
        return chatMessageRepository.save(chatMessage);
    }

    @Override
    public Optional<ChatMessage> findMessageById(UUID messageId) {
        return chatMessageRepository.findById(messageId);
    }

    @Override
    public List<ChatMessage> findMessagesBySessionIdOrderByCreatedAtAsc(UUID sessionId) {
        return chatMessageRepository.findByChatSessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Override
    public List<ChatCitation> findCitationsByMessageIdsOrderByMessageIdAndId(List<UUID> messageIds) {
        return chatCitationRepository.findByChatMessageIdInOrderByChatMessageIdAscIdAsc(messageIds);
    }

    @Override
    public ChatCitation saveCitation(ChatCitation chatCitation) {
        return chatCitationRepository.save(chatCitation);
    }
}
