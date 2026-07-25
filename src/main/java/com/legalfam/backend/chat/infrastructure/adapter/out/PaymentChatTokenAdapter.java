package com.legalfam.backend.chat.infrastructure.adapter.out;

import com.legalfam.backend.chat.application.port.out.IChatTokenPort;
import com.legalfam.backend.payment.application.port.in.IPaymentTokenUseCase;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentChatTokenAdapter implements IChatTokenPort {

    private final IPaymentTokenUseCase IPaymentTokenUseCase;

    public PaymentChatTokenAdapter(IPaymentTokenUseCase IPaymentTokenUseCase) {
        this.IPaymentTokenUseCase = IPaymentTokenUseCase;
    }

    @Override
    public void consumeChatTokensForAssistantResult(UUID userId, UUID chatMessageId, int tokenCost) {
        IPaymentTokenUseCase.consumeChatTokensForAssistantResult(userId, chatMessageId, tokenCost);
    }

    @Override
    public boolean hasChatTokensAvailable(UUID userId) {
        return IPaymentTokenUseCase.hasChatTokensAvailable(userId);
    }
}
