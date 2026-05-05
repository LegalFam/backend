package com.legalfam.backend.chat.infrastructure.persistence;

import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatOutboxEventEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatOutboxEventRepository extends JpaRepository<ChatOutboxEventEntity, UUID> {

    @Query(
            value = """
                    select *
                    from chat_outbox_event
                    where status in ('PENDING', 'FAILED')
                      and available_at <= :now
                    order by created_at asc
                    limit :batchSize
                    for update skip locked
                    """,
            nativeQuery = true
    )
    List<ChatOutboxEventEntity> lockReadyBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Modifying
    long deleteByStatusAndPublishedAtBefore(ChatOutboxEventStatus status, Instant threshold);

    @Modifying
    long deleteByStatusInAndUpdatedAtBefore(Collection<ChatOutboxEventStatus> statuses, Instant threshold);
}
