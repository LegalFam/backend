package com.legalfam.backend.auth.infrastructure.adapter.out;

import com.legalfam.backend.auth.application.port.out.IAuthMailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Used when app.auth.mail.enabled=false (local dev and the test profile): prints the
 * action link instead of contacting SMTP, so both flows are exercisable without a mail server.
 */
@Component
@ConditionalOnProperty(prefix = "app.auth.mail", name = "enabled", havingValue = "false")
public class LoggingAuthMailAdapter implements IAuthMailPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuthMailAdapter.class);

    @Override
    public void sendEmailVerification(
            String toEmail,
            String recipientName,
            String verificationUrl,
            long expiresInMinutes
    ) {
        log.info("[mail disabled] Email verification for {} ({} min): {}",
                toEmail, expiresInMinutes, verificationUrl);
    }

    @Override
    public void sendPasswordReset(String toEmail, String recipientName, String resetUrl, long expiresInMinutes) {
        log.info("[mail disabled] Password reset for {} ({} min): {}", toEmail, expiresInMinutes, resetUrl);
    }
}
