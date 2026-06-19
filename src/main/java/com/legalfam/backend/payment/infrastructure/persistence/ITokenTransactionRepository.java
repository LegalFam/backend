package com.legalfam.backend.payment.infrastructure.persistence;

import com.legalfam.backend.payment.infrastructure.persistence.entity.TokenTransactionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ITokenTransactionRepository extends JpaRepository<TokenTransactionEntity, UUID> {
    boolean existsByChatMessageIdAndType(UUID chatMessageId, String type);
}
