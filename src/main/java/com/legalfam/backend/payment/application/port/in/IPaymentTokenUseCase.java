package com.legalfam.backend.payment.application.port.in;

import java.util.UUID;

public interface IPaymentTokenUseCase {
    void consumeChatTokensForAssistantResult(UUID userId, UUID chatMessageId, int tokenCost);

    void refundChatToken(UUID chatMessageId);
}
