package com.legalfam.backend.payment.application.port.in;

import java.util.UUID;

public interface IPaymentTokenUseCase {
    void consumeChatToken(UUID userId, UUID chatMessageId);

    void refundChatToken(UUID chatMessageId);
}
