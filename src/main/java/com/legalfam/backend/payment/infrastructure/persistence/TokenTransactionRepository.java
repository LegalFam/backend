package com.legalfam.backend.payment.infrastructure.persistence;

import com.legalfam.backend.payment.infrastructure.persistence.entity.TokenTransactionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenTransactionRepository extends JpaRepository<TokenTransactionEntity, UUID> {
    Optional<TokenTransactionEntity> findByChatMessageIdAndType(UUID chatMessageId, String type);

    boolean existsByChatMessageIdAndType(UUID chatMessageId, String type);
}
