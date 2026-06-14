package com.legalfam.backend.payment.infrastructure.adapter.out;

import com.legalfam.backend.payment.application.dto.PaymentPlanDefinition;
import com.legalfam.backend.payment.application.dto.PaymentSubscriptionSnapshot;
import com.legalfam.backend.payment.application.dto.PaymentWebhookNotification;
import com.legalfam.backend.payment.application.port.out.IPaymentGatewayPort;
import com.legalfam.backend.payment.domain.exception.PaymentGatewayException;
import com.legalfam.backend.payment.domain.exception.PaymentWebhookException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class MercadoPagoPaymentGatewayAdapter implements IPaymentGatewayPort {

    private static final int TIMEOUT_MS = 15000;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String accessToken;
    private final String apiBaseUrl;

    public MercadoPagoPaymentGatewayAdapter(
            ObjectMapper objectMapper,
            @Value("${app.payment.mercado-pago.access-token:}") String accessToken,
            @Value("${app.payment.mercado-pago.api-base-url:https://api.mercadopago.com}") String apiBaseUrl
    ) {
        this.objectMapper = objectMapper;
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank()
                ? "https://api.mercadopago.com"
                : apiBaseUrl.trim().replaceAll("/+$", "");
        this.restTemplate = buildRestTemplate();
    }

    @Override
    public String createCheckoutSession(
            UUID userId,
            String email,
            PaymentPlanDefinition plan,
            String successUrl,
            String cancelUrl
    ) {
        requireAccessToken();
        if (email == null || email.isBlank()) {
            throw new PaymentGatewayException("Mercado Pago requires a payer email");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("reason", plan.displayName());
        payload.put("external_reference", buildExternalReference(userId, plan.code().name()));
        payload.put("payer_email", email.trim());
        payload.put("back_url", successUrl);
        payload.put("status", "pending");

        ObjectNode autoRecurring = payload.putObject("auto_recurring");
        autoRecurring.put("frequency", 1);
        autoRecurring.put("frequency_type", "months");
        autoRecurring.put("transaction_amount", BigDecimal.valueOf(plan.monthlyPriceCents()).movePointLeft(2));
        autoRecurring.put("currency_id", plan.currency().toUpperCase(Locale.ROOT));

        JsonNode response = exchangeJson(HttpMethod.POST, "/preapproval", payload.toString());
        String url = firstNonBlank(readText(response, "init_point"), readText(response, "sandbox_init_point"));
        if (url == null) {
            throw new PaymentGatewayException("Mercado Pago did not return a checkout URL");
        }
        return url;
    }

    @Override
    public void cancelSubscription(String subscriptionId) {
        requireAccessToken();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new PaymentGatewayException("Subscription id is required");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status", "cancelled");
        exchangeJson(HttpMethod.PUT, "/preapproval/" + subscriptionId.trim(), payload.toString());
    }

    @Override
    public PaymentWebhookNotification parseWebhook(String payload, String signatureHeader) {
        requireAccessToken();
        JsonNode root = readTree(payload);
        String eventId = firstNonBlank(readText(root, "id"), readText(root, "action"));
        String eventType = firstNonBlank(readText(root, "type"), readText(root, "action"));
        String resourceId = readNestedText(root, "data", "id");
        if (resourceId == null) {
            return new PaymentWebhookNotification(eventId, eventType, null, null, null, null,
                    null, false, null, null, false);
        }

        String normalizedType = eventType == null ? "" : eventType.trim().toLowerCase(Locale.ROOT);
        if (normalizedType.equals("payment")) {
            return mapPaymentWebhook(eventId, eventType, resourceId);
        }
        if (normalizedType.contains("preapproval") || normalizedType.contains("subscription")) {
            PaymentSubscriptionSnapshot snapshot = fetchSubscriptionSnapshot(resourceId);
            return new PaymentWebhookNotification(
                    eventId,
                    eventType,
                    snapshot.customerId(),
                    snapshot.subscriptionId(),
                    snapshot.userId(),
                    snapshot.planCode(),
                    snapshot.status(),
                    snapshot.cancelAtPeriodEnd(),
                    snapshot.currentPeriodStart(),
                    snapshot.currentPeriodEnd(),
                    false
            );
        }

        return new PaymentWebhookNotification(eventId, eventType, null, null, null, null,
                null, false, null, null, false);
    }

    @Override
    public PaymentSubscriptionSnapshot fetchSubscriptionSnapshot(String subscriptionId) {
        requireAccessToken();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new PaymentGatewayException("Subscription id is required");
        }
        JsonNode root = exchangeJson(HttpMethod.GET, "/preapproval/" + subscriptionId.trim(), null);
        return toSubscriptionSnapshot(root, null);
    }

    private PaymentWebhookNotification mapPaymentWebhook(String eventId, String eventType, String paymentId) {
        JsonNode payment = exchangeJson(HttpMethod.GET, "/v1/payments/" + paymentId, null);
        String subscriptionId = firstNonBlank(
                readText(payment, "preapproval_id"),
                readNestedText(payment, "metadata", "preapproval_id"),
                readNestedText(payment, "metadata", "subscription_id")
        );
        if (subscriptionId == null) {
            return new PaymentWebhookNotification(eventId, eventType, null, null, null, null,
                    readText(payment, "status"), false, null, null, false);
        }

        Instant approvedAt = parseInstant(readText(payment, "date_approved"));
        PaymentSubscriptionSnapshot snapshot = fetchSubscriptionSnapshot(subscriptionId);
        return new PaymentWebhookNotification(
                eventId,
                eventType,
                snapshot.customerId(),
                snapshot.subscriptionId(),
                snapshot.userId(),
                snapshot.planCode(),
                snapshot.status(),
                snapshot.cancelAtPeriodEnd(),
                approvedAt == null ? snapshot.currentPeriodStart() : approvedAt,
                approvedAt == null ? snapshot.currentPeriodEnd() : addMonths(approvedAt, 1),
                "approved".equalsIgnoreCase(readText(payment, "status"))
        );
    }

    private PaymentSubscriptionSnapshot toSubscriptionSnapshot(JsonNode root, Instant periodStartOverride) {
        ParsedExternalReference externalReference = parseExternalReference(readText(root, "external_reference"));
        Instant periodStart = periodStartOverride != null
                ? periodStartOverride
                : firstNonNull(
                        parseInstant(readText(root, "last_modified")),
                        parseInstant(readText(root, "date_created"))
                );
        Instant periodEnd = parseInstant(readText(root, "next_payment_date"));
        if (periodStart != null && (periodEnd == null || !periodEnd.isAfter(periodStart))) {
            periodEnd = addMonths(periodStart, 1);
        }

        return new PaymentSubscriptionSnapshot(
                firstNonBlank(readText(root, "payer_id"), readText(root, "payer_email")),
                readText(root, "id"),
                externalReference.userId(),
                externalReference.planCode(),
                readText(root, "status"),
                false,
                periodStart,
                periodEnd
        );
    }

    private JsonNode exchangeJson(HttpMethod method, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        HttpEntity<String> request = new HttpEntity<>(body, headers);
        String url = apiBaseUrl + path;
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, method, request, String.class);
            if (response.getBody() == null || response.getBody().isBlank()) {
                throw new PaymentGatewayException("Payment gateway returned an empty response");
            }
            return readTree(response.getBody());
        } catch (HttpStatusCodeException ex) {
            throw new PaymentGatewayException("Payment gateway request failed with status " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw new PaymentGatewayException("Payment gateway is unavailable", ex);
        }
    }

    private JsonNode readTree(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new PaymentWebhookException("Webhook payload is invalid", ex);
        }
    }

    private String buildExternalReference(UUID userId, String planCode) {
        return "userId=" + userId + ";planCode=" + planCode;
    }

    private ParsedExternalReference parseExternalReference(String externalReference) {
        if (externalReference == null || externalReference.isBlank()) {
            return new ParsedExternalReference(null, null);
        }

        UUID userId = null;
        String planCode = null;
        for (String part : externalReference.split(";")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            String key = pair[0].trim();
            String value = pair[1].trim();
            if (key.equals("userId")) {
                try {
                    userId = UUID.fromString(value);
                } catch (IllegalArgumentException ignored) {
                    throw new PaymentWebhookException("Webhook user reference is invalid");
                }
            }
            if (key.equals("planCode")) {
                planCode = value;
            }
        }
        return new ParsedExternalReference(userId, planCode);
    }

    private String readText(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        JsonNode child = node.get(fieldName);
        if (child == null || child.isNull()) {
            return null;
        }
        String value = child.asString();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String readNestedText(JsonNode node, String objectField, String childField) {
        if (node == null || objectField == null || childField == null) {
            return null;
        }
        JsonNode child = node.get(objectField);
        return readText(child, childField);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return ZonedDateTime.parse(value).toInstant();
        } catch (Exception ignored) {
        }
        return null;
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private String firstNonBlank(String first, String second, String third) {
        return firstNonBlank(firstNonBlank(first, second), third);
    }

    private Instant addMonths(Instant instant, int months) {
        return instant.atZone(ZoneOffset.UTC).plusMonths(months).toInstant();
    }

    private void requireAccessToken() {
        if (accessToken.isBlank()) {
            throw new PaymentGatewayException("Mercado Pago access token is not configured");
        }
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);
        return new RestTemplate(requestFactory);
    }

    private record ParsedExternalReference(UUID userId, String planCode) {
    }
}
