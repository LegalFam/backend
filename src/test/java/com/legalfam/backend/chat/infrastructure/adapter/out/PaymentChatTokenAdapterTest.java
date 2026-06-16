package com.legalfam.backend.chat.infrastructure.adapter.out;

import static org.mockito.Mockito.verify;

import com.legalfam.backend.payment.application.port.in.IPaymentTokenUseCase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentChatTokenAdapterTest {

    @Mock
    private IPaymentTokenUseCase IPaymentTokenUseCase;

    @InjectMocks
    private PaymentChatTokenAdapter paymentChatTokenAdapter;

    @Test
    void consumeChatTokensForAssistantResultDelegatesToPaymentUseCase() {
        UUID userId = UUID.randomUUID();
        UUID chatMessageId = UUID.randomUUID();

        paymentChatTokenAdapter.consumeChatTokensForAssistantResult(userId, chatMessageId, 3);

        verify(IPaymentTokenUseCase).consumeChatTokensForAssistantResult(userId, chatMessageId, 3);
    }

    @Test
    void refundChatTokenDelegatesToPaymentUseCase() {
        UUID chatMessageId = UUID.randomUUID();

        paymentChatTokenAdapter.refundChatToken(chatMessageId);

        verify(IPaymentTokenUseCase).refundChatToken(chatMessageId);
    }
}
