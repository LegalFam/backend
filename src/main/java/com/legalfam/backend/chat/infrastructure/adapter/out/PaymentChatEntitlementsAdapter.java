package com.legalfam.backend.chat.infrastructure.adapter.out;

import com.legalfam.backend.chat.application.dto.ChatEntitlements;
import com.legalfam.backend.chat.application.port.out.IChatEntitlementsPort;
import com.legalfam.backend.payment.application.dto.PaymentEntitlements;
import com.legalfam.backend.payment.application.port.in.IPaymentEntitlementsUseCase;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentChatEntitlementsAdapter implements IChatEntitlementsPort {

    private final IPaymentEntitlementsUseCase IPaymentEntitlementsUseCase;

    public PaymentChatEntitlementsAdapter(IPaymentEntitlementsUseCase IPaymentEntitlementsUseCase) {
        this.IPaymentEntitlementsUseCase = IPaymentEntitlementsUseCase;
    }

    @Override
    public ChatEntitlements resolveEntitlements(UUID userId) {
        PaymentEntitlements entitlements = IPaymentEntitlementsUseCase.resolveEntitlements(userId);
        return new ChatEntitlements(entitlements.contextMessageLimit(), entitlements.historyWindowDays());
    }
}
