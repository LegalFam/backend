package com.legalfam.backend.payment.infrastructure.adapter.in;

import com.legalfam.backend.common.identity.event.UserRegisteredEvent;
import com.legalfam.backend.payment.application.port.in.IPaymentProvisioningUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class UserRegisteredPaymentListener {

    private final IPaymentProvisioningUseCase IPaymentProvisioningUseCase;

    public UserRegisteredPaymentListener(IPaymentProvisioningUseCase IPaymentProvisioningUseCase) {
        this.IPaymentProvisioningUseCase = IPaymentProvisioningUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void provisionFreeSubscription(UserRegisteredEvent event) {
        IPaymentProvisioningUseCase.provisionFreeSubscription(event.userId());
    }
}
