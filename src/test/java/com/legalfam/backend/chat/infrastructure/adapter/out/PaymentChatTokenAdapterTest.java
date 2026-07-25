package com.legalfam.backend.chat.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void hasChatTokensAvailableDelegatesToPaymentUseCase() {
        UUID userId = UUID.randomUUID();
        when(IPaymentTokenUseCase.hasChatTokensAvailable(userId)).thenReturn(true);

        assertTrue(paymentChatTokenAdapter.hasChatTokensAvailable(userId));
        verify(IPaymentTokenUseCase).hasChatTokensAvailable(userId);
    }

    @Test
    void hasChatTokensAvailableReturnsFalseWhenPaymentUseCaseReturnsFalse() {
        UUID userId = UUID.randomUUID();
        when(IPaymentTokenUseCase.hasChatTokensAvailable(userId)).thenReturn(false);

        assertFalse(paymentChatTokenAdapter.hasChatTokensAvailable(userId));
    }
}
