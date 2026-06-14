package com.legalfam.backend.chat.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.chat.messaging.rabbit")
public record ChatRabbitProperties(
        @NotBlank String exchangeName,
        @NotBlank String deadLetterExchangeName,
        @NotBlank String assistantDeliveryQueueName,
        @NotBlank String deadLetterQueueName,
        @NotBlank String assistantDeliveryRoutingKey,
        @NotBlank String deadLetterRoutingKey,
        @Positive Long queueTtlMs,
        @Positive Long publisherConfirmTimeoutMs
) {
    public long safeQueueTtlMs() {
        return queueTtlMs == null ? 10800000L : queueTtlMs;
    }

    public long safePublisherConfirmTimeoutMs() {
        return publisherConfirmTimeoutMs == null ? 5000L : publisherConfirmTimeoutMs;
    }
}
