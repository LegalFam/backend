package com.legalfam.backend.chat.infrastructure.integration;

import com.legalfam.backend.chat.application.port.out.ChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.chat.outbox.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class ChatOutboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ChatOutboxCleanupJob.class);
    private static final Duration PUBLISHED_RETENTION = Duration.ofDays(7);
    private static final Duration FAILED_RETENTION = Duration.ofDays(30);

    private final ChatPersistencePort chatPersistencePort;

    public ChatOutboxCleanupJob(ChatPersistencePort chatPersistencePort) {
        this.chatPersistencePort = chatPersistencePort;
    }

    @Scheduled(cron = "${app.chat.outbox.cleanup.cron:0 0 * * * *}")
    @Transactional
    public void cleanup() {
        Instant now = Instant.now();
        long deletedPublished = chatPersistencePort.deleteOutboxEventsByStatusAndPublishedAtBefore(
                ChatOutboxEventStatus.PUBLISHED,
                now.minus(PUBLISHED_RETENTION)
        );
        long deletedFailedOrDead = chatPersistencePort.deleteOutboxEventsByStatusInAndUpdatedAtBefore(
                List.of(ChatOutboxEventStatus.FAILED, ChatOutboxEventStatus.DEAD),
                now.minus(FAILED_RETENTION)
        );

        if (deletedPublished > 0 || deletedFailedOrDead > 0) {
            log.info("Cleaned chat outbox events publishedDeleted={} failedOrDeadDeleted={}",
                    deletedPublished, deletedFailedOrDead);
        }
    }
}
