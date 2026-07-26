package com.legalfam.backend.auth.infrastructure.adapter.in;

import com.legalfam.backend.auth.application.port.in.IAuthMailDispatchUseCase;
import com.legalfam.backend.common.identity.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the verification email only after the signup transaction commits, so a mail
 * failure can never roll the new account back. Not transactional itself: the dispatcher
 * opens its own transaction to mint the token before handing it to the mail port.
 */
@Service
public class UserRegisteredMailListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredMailListener.class);

    private final IAuthMailDispatchUseCase IAuthMailDispatchUseCase;

    public UserRegisteredMailListener(IAuthMailDispatchUseCase IAuthMailDispatchUseCase) {
        this.IAuthMailDispatchUseCase = IAuthMailDispatchUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendVerificationEmail(UserRegisteredEvent event) {
        try {
            IAuthMailDispatchUseCase.dispatchEmailVerification(event.userId());
        } catch (RuntimeException ex) {
            // The account already exists; the user can always request a new link.
            log.error("Failed to dispatch verification email for user {}", event.userId(), ex);
        }
    }
}
