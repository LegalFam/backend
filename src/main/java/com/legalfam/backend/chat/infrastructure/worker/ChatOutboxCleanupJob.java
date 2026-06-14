package com.legalfam.backend.chat.infrastructure.worker;

import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import java.time.Duration;
import java.time.Instant;
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
    private static final Duration READ_RETENTION = Duration.ofDays(7);

    private final IChatPersistencePort IChatPersistencePort;

    public ChatOutboxCleanupJob(IChatPersistencePort IChatPersistencePort) {
        this.IChatPersistencePort = IChatPersistencePort;
    }

    @Scheduled(cron = "${app.chat.outbox.cleanup.cron:0 0 * * * *}")
    @Transactional
    public void cleanup() {
        Instant now = Instant.now();
        long deletedRead = IChatPersistencePort.deleteOutboxEventsByStatusAndReadAtBefore(
                ChatOutboxEventStatus.READ,
                now.minus(READ_RETENTION)
        );

        if (deletedRead > 0) {
            log.info("Cleaned chat outbox events readDeleted={}", deletedRead);
        }
    }
}
