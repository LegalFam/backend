package com.legalfam.backend.payment.infrastructure.adapter.in;

import static org.mockito.Mockito.verify;

import com.legalfam.backend.common.event.UserRegisteredEvent;
import com.legalfam.backend.payment.application.port.in.IPaymentProvisioningUseCase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserRegisteredPaymentListenerTest {

    @Mock
    private IPaymentProvisioningUseCase IPaymentProvisioningUseCase;

    @InjectMocks
    private UserRegisteredPaymentListener userRegisteredPaymentListener;

    @Test
    void provisionFreeSubscriptionDelegatesToPaymentUseCase() {
        UUID userId = UUID.randomUUID();

        userRegisteredPaymentListener.provisionFreeSubscription(new UserRegisteredEvent(userId));

        verify(IPaymentProvisioningUseCase).provisionFreeSubscription(userId);
    }
}
