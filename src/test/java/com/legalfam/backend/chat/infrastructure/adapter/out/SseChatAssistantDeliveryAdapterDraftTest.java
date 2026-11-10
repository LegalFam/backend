package com.legalfam.backend.chat.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.legalfam.backend.chat.application.event.ChatAssistantDraftEvent;
import com.legalfam.backend.chat.infrastructure.config.ChatSseProperties;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SseChatAssistantDeliveryAdapterDraftTest {

    private final SseChatAssistantDeliveryAdapter adapter =
            new SseChatAssistantDeliveryAdapter(new ChatSseProperties(60000L, 60000L));

    @AfterEach
    void shutdown() {
        adapter.shutdown();
    }

    @Test
    void draftReachesTheSubscriberOfThatSession() {
        UUID sessionId = UUID.randomUUID();
        adapter.subscribe(UUID.randomUUID(), sessionId);

        assertTrue(adapter.dispatchAssistantDraft(new ChatAssistantDraftEvent(sessionId, UUID.randomUUID(), "Hola", 1)));
    }

    @Test
    void draftForASessionNobodyWatchesHereIsDropped() {
        adapter.subscribe(UUID.randomUUID(), UUID.randomUUID());

        assertFalse(adapter.dispatchAssistantDraft(new ChatAssistantDraftEvent(UUID.randomUUID(), UUID.randomUUID(), "Hola", 1)));
    }
}
