package com.legalfam.backend.payment.infrastructure.adapter.out;

import com.legalfam.backend.payment.application.dto.PaymentPlanDefinition;
import com.legalfam.backend.payment.application.dto.PaymentSubscriptionSnapshot;
import com.legalfam.backend.payment.application.dto.PaymentWebhookNotification;
import com.legalfam.backend.payment.application.port.out.IPaymentGatewayPort;
import com.legalfam.backend.payment.domain.exception.PaymentGatewayException;
import com.legalfam.backend.payment.domain.exception.PaymentApiError;
import com.legalfam.backend.payment.domain.exception.PaymentWebhookException;
import com.legalfam.backend.payment.infrastructure.config.MercadoPagoProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
    private final String webhookSecret;

    public MercadoPagoPaymentGatewayAdapter(ObjectMapper objectMapper, MercadoPagoProperties mercadoPagoProperties) {
        this.objectMapper = objectMapper;
        this.accessToken = mercadoPagoProperties.normalizedAccessToken();
        this.apiBaseUrl = mercadoPagoProperties.normalizedApiBaseUrl();
        this.webhookSecret = mercadoPagoProperties.normalizedWebhookSecret();
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
            throw PaymentGatewayException.of(PaymentApiError.PAYMENT_GATEWAY_PAYER_EMAIL_REQUIRED);
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
            throw PaymentGatewayException.of(PaymentApiError.PAYMENT_GATEWAY_CHECKOUT_URL_MISSING);
        }
        return url;
    }

    @Override
    public void cancelSubscription(String subscriptionId) {
        requireAccessToken();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw PaymentGatewayException.of(PaymentApiError.PAYMENT_GATEWAY_SUBSCRIPTION_ID_REQUIRED);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("status", "cancelled");
        exchangeJson(HttpMethod.PUT, "/preapproval/" + subscriptionId.trim(), payload.toString());
    }

    @Override
    public PaymentWebhookNotification parseVerifiedWebhook(
            String payload,
            String signatureHeader,
            String requestId,
            String dataId
    ) {
        requireAccessToken();
        verifyWebhookSignature(signatureHeader, requestId, dataId);
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
            throw PaymentGatewayException.of(PaymentApiError.PAYMENT_GATEWAY_SUBSCRIPTION_ID_REQUIRED);
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
                throw PaymentGatewayException.of(PaymentApiError.PAYMENT_GATEWAY_EMPTY_RESPONSE);
            }
            return readTree(response.getBody());
        } catch (HttpStatusCodeException ex) {
            throw PaymentGatewayException.of(PaymentApiError.PAYMENT_GATEWAY_UNAVAILABLE, ex);
        } catch (ResourceAccessException ex) {
            throw PaymentGatewayException.of(PaymentApiError.PAYMENT_GATEWAY_UNAVAILABLE, ex);
        }
    }

    private JsonNode readTree(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw PaymentWebhookException.of(PaymentApiError.WEBHOOK_PAYLOAD_INVALID, ex);
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
                    throw PaymentWebhookException.of(PaymentApiError.WEBHOOK_USER_REFERENCE_INVALID);
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
            throw PaymentGatewayException.of(PaymentApiError.PAYMENT_GATEWAY_MISCONFIGURED);
        }
    }

    private void verifyWebhookSignature(String signatureHeader, String requestId, String dataId) {
        if (webhookSecret.isBlank()) {
            return;
        }
        SignatureParts signatureParts = parseSignature(signatureHeader);
        if (requestId == null || requestId.isBlank()) {
            throw PaymentWebhookException.of(PaymentApiError.WEBHOOK_REQUEST_ID_REQUIRED);
        }
        if (dataId == null || dataId.isBlank()) {
            throw PaymentWebhookException.of(PaymentApiError.WEBHOOK_DATA_ID_REQUIRED);
        }

        String manifest = "id:" + normalizeDataId(dataId)
                + ";request-id:" + requestId.trim()
                + ";ts:" + signatureParts.timestamp()
                + ";";
        String expectedSignature = hmacSha256Hex(manifest, webhookSecret);
        if (!constantTimeEquals(expectedSignature, signatureParts.signature())) {
            throw PaymentWebhookException.of(PaymentApiError.WEBHOOK_SIGNATURE_INVALID);
        }
    }

    private SignatureParts parseSignature(String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw PaymentWebhookException.of(PaymentApiError.WEBHOOK_SIGNATURE_REQUIRED);
        }
        String timestamp = null;
        String signature = null;
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            String key = pair[0].trim();
            String value = pair[1].trim();
            if ("ts".equals(key)) {
                timestamp = value;
            }
            if ("v1".equals(key)) {
                signature = value;
            }
        }
        if (timestamp == null || timestamp.isBlank() || signature == null || signature.isBlank()) {
            throw PaymentWebhookException.of(PaymentApiError.WEBHOOK_SIGNATURE_INVALID);
        }
        return new SignatureParts(timestamp, signature);
    }

    private String normalizeDataId(String dataId) {
        String normalized = dataId.trim();
        return normalized.matches("[A-Za-z0-9]+") ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    private String hmacSha256Hex(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw PaymentWebhookException.of(PaymentApiError.WEBHOOK_SIGNATURE_UNVERIFIABLE, ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);
        return new RestTemplate(requestFactory);
    }

    private record ParsedExternalReference(UUID userId, String planCode) {
    }

    private record SignatureParts(String timestamp, String signature) {
    }
}
