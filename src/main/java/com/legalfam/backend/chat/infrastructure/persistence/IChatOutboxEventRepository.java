package com.legalfam.backend.chat.infrastructure.persistence;

import com.legalfam.backend.chat.domain.model.ChatOutboxEventStatus;
import com.legalfam.backend.chat.infrastructure.persistence.entity.ChatOutboxEventEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IChatOutboxEventRepository extends JpaRepository<ChatOutboxEventEntity, UUID> {

    Optional<ChatOutboxEventEntity> findByAggregateId(UUID aggregateId);

    List<ChatOutboxEventEntity> findByAggregateIdIn(List<UUID> aggregateIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from ChatOutboxEventEntity event where event.aggregateId = :aggregateId")
    ChatOutboxEventEntity findByAggregateIdForUpdate(@Param("aggregateId") UUID aggregateId);

    @Query(
            value = """
                    select exists(
                        select 1
                        from chat_outbox_event event
                        where event.chat_session_id = :sessionId
                          and event.status <> 'READ'
                    )
                    """,
            nativeQuery = true
    )
    boolean existsUnreadAssistantMessageBySessionId(@Param("sessionId") UUID sessionId);

    @Query(
            value = """
                    select *
                    from chat_outbox_event
                    where status in ('PENDING', 'PUBLISHED')
                      and available_at <= :now
                    order by created_at asc
                    limit :batchSize
                    for update skip locked
                    """,
            nativeQuery = true
    )
    List<ChatOutboxEventEntity> lockReadyBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Modifying
    long deleteByStatusAndReadAtBefore(ChatOutboxEventStatus status, Instant threshold);

    @Modifying
    long deleteByChatSessionId(UUID chatSessionId);

    @Modifying
    long deleteByStatusInAndUpdatedAtBefore(Collection<ChatOutboxEventStatus> statuses, Instant threshold);
}
