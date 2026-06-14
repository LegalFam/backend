package com.legalfam.backend.chat.application.policy;

import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.application.port.out.IChatUserLookupPort;
import com.legalfam.backend.chat.domain.exception.ChatAccessDeniedException;
import com.legalfam.backend.chat.domain.exception.ChatNotFoundException;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatSession;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChatAccessPolicy {

    private final IChatPersistencePort IChatPersistencePort;
    private final IChatUserLookupPort IChatUserLookupPort;

    public ChatAccessPolicy(IChatPersistencePort IChatPersistencePort, IChatUserLookupPort IChatUserLookupPort) {
        this.IChatPersistencePort = IChatPersistencePort;
        this.IChatUserLookupPort = IChatUserLookupPort;
    }

    public void assertUserExists(UUID userId) {
        if (!IChatUserLookupPort.existsById(userId)) {
            throw new ChatAccessDeniedException("Authenticated user not found");
        }
    }

    public ChatSession requireSessionOwner(UUID userId, UUID sessionId) {
        ChatSession session = IChatPersistencePort.findSessionById(sessionId)
                .orElseThrow(() -> new ChatNotFoundException("Chat session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new ChatAccessDeniedException("Access is forbidden");
        }
        return session;
    }

    public ChatSession requireMessageSessionOwner(UUID userId, ChatMessage message) {
        ChatSession messageSession = IChatPersistencePort.findSessionById(message.getChatSessionId())
                .orElseThrow(() -> new ChatNotFoundException("Chat session not found"));
        if (!messageSession.getUserId().equals(userId)) {
            throw new ChatAccessDeniedException("Access is forbidden");
        }
        return messageSession;
    }
}
