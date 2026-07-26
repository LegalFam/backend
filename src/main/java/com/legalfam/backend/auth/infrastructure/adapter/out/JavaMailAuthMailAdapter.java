package com.legalfam.backend.auth.infrastructure.adapter.out;

import com.legalfam.backend.auth.application.port.out.IAuthMailPort;
import com.legalfam.backend.auth.infrastructure.config.AuthMailProperties;
import com.legalfam.backend.auth.infrastructure.mail.AuthMailTemplateRenderer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Sends synchronously on purpose. Cloud Run only allocates CPU while a request is being processed,
 * so an @Async send is frozen the moment the response is returned: the SMTP socket opens, the thread
 * stalls, the server drops the connection, and the send dies with "Can't send command to SMTP host".
 * Running inside the request costs a second or two of latency and makes delivery reliable.
 * The send still cannot break signup: it runs after the transaction commits and failures are logged.
 */
@Component
@ConditionalOnProperty(prefix = "app.auth.mail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JavaMailAuthMailAdapter implements IAuthMailPort {

    private static final Logger log = LoggerFactory.getLogger(JavaMailAuthMailAdapter.class);

    private static final String VERIFY_SUBJECT = "Confirma tu correo";
    private static final String RESET_SUBJECT = "Restablece tu contraseña";

    private final JavaMailSender mailSender;
    private final AuthMailProperties authMailProperties;
    private final AuthMailTemplateRenderer templateRenderer;

    public JavaMailAuthMailAdapter(
            JavaMailSender mailSender,
            AuthMailProperties authMailProperties,
            AuthMailTemplateRenderer templateRenderer
    ) {
        this.mailSender = mailSender;
        this.authMailProperties = authMailProperties;
        this.templateRenderer = templateRenderer;
    }

    @Override
    public void sendEmailVerification(
            String toEmail,
            String recipientName,
            String verificationUrl,
            long expiresInMinutes
    ) {
        send(toEmail, recipientName, verificationUrl, expiresInMinutes, VERIFY_SUBJECT, "verify-email");
    }

    @Override
    public void sendPasswordReset(String toEmail, String recipientName, String resetUrl, long expiresInMinutes) {
        send(toEmail, recipientName, resetUrl, expiresInMinutes, RESET_SUBJECT, "reset-password");
    }

    private void send(
            String toEmail,
            String recipientName,
            String actionUrl,
            long expiresInMinutes,
            String subject,
            String template
    ) {
        Map<String, String> values = Map.of(
                "name", recipientName == null ? "" : recipientName,
                "actionUrl", actionUrl,
                "expiresInMinutes", String.valueOf(expiresInMinutes),
                "appName", authMailProperties.fromName()
        );

        // Never propagate: the account already exists and the user can request a new link.
        // The token never reaches the log.
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setFrom(new InternetAddress(authMailProperties.from(), authMailProperties.fromName()));
            if (StringUtils.hasText(authMailProperties.replyTo())) {
                helper.setReplyTo(authMailProperties.replyTo());
            }
            helper.setText(
                    templateRenderer.render(template + ".txt", values),
                    templateRenderer.render(template + ".html", values)
            );
            mailSender.send(message);
        } catch (UnsupportedEncodingException | MessagingException | RuntimeException ex) {
            log.error("Failed to send '{}' auth email to {}", template, toEmail, ex);
        }
    }
}
