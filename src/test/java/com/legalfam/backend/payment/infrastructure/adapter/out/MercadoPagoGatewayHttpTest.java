package com.legalfam.backend.payment.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.legalfam.backend.payment.application.dto.PaymentPlanDefinition;
import com.legalfam.backend.payment.application.dto.PaymentSubscriptionSnapshot;
import com.legalfam.backend.payment.application.dto.PaymentWebhookNotification;
import com.legalfam.backend.payment.domain.exception.PaymentApiError;
import com.legalfam.backend.payment.domain.exception.PaymentGatewayException;
import com.legalfam.backend.payment.domain.exception.PaymentWebhookException;
import com.legalfam.backend.payment.domain.model.SubscriptionPlanCode;
import com.legalfam.backend.payment.infrastructure.config.MercadoPagoProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MercadoPagoGatewayHttpTest {

    private static final String SECRET = "webhook-secret";
    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String SUBSCRIPTION = """
            {"id":"sub_1","payer_id":"payer_9","status":"authorized",
             "external_reference":"userId=11111111-2222-3333-4444-555555555555;planCode=BASIC",
             "date_created":"2026-11-01T10:00:00.000-05:00","next_payment_date":"2026-11-01T10:00:00Z"}
            """;

    private final Map<String, Response> routes = new HashMap<>();
    private final Map<String, String> receivedBodies = new HashMap<>();
    private HttpServer server;
    private MercadoPagoPaymentGatewayAdapter adapter;

    private record Response(int status, String body) {
    }

    @BeforeEach
    void startFakeApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
            receivedBodies.put(key, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Response response = routes.getOrDefault(key, new Response(404, "{}"));
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        adapter = adapter("access-token", SECRET);
    }

    @AfterEach
    void stopFakeApi() {
        server.stop(0);
    }

    @Test
    void createsCheckoutAndReturnsInitPoint() {
        routes.put("POST /preapproval", new Response(201, "{\"init_point\":\"https://mp.test/checkout\"}"));

        String url = adapter.createCheckoutSession(USER_ID, " ana@legalfam.test ", basicPlan(), "https://ok", "https://ko");

        assertThat(url).isEqualTo("https://mp.test/checkout");
        assertThat(receivedBodies.get("POST /preapproval"))
                .contains("\"payer_email\":\"ana@legalfam.test\"")
                .contains("planCode=BASIC")
                .contains("\"currency_id\":\"PEN\"")
                .contains("19.90");
    }

    @Test
    void fallsBackToSandboxUrlAndFailsWithoutAnyUrl() {
        routes.put("POST /preapproval", new Response(201, "{\"sandbox_init_point\":\"https://sandbox\"}"));
        assertThat(adapter.createCheckoutSession(USER_ID, "a@b.c", basicPlan(), "s", "c")).isEqualTo("https://sandbox");

        routes.put("POST /preapproval", new Response(201, "{\"id\":\"x\"}"));
        assertGatewayError(() -> adapter.createCheckoutSession(USER_ID, "a@b.c", basicPlan(), "s", "c"),
                PaymentApiError.PAYMENT_GATEWAY_CHECKOUT_URL_MISSING);
    }

    @Test
    void validatesInputsBeforeCallingTheApi() {
        assertGatewayError(() -> adapter.createCheckoutSession(USER_ID, " ", basicPlan(), "s", "c"),
                PaymentApiError.PAYMENT_GATEWAY_PAYER_EMAIL_REQUIRED);
        assertGatewayError(() -> adapter.cancelSubscription(" "),
                PaymentApiError.PAYMENT_GATEWAY_SUBSCRIPTION_ID_REQUIRED);
        assertGatewayError(() -> adapter.fetchSubscriptionSnapshot(null),
                PaymentApiError.PAYMENT_GATEWAY_SUBSCRIPTION_ID_REQUIRED);
        assertGatewayError(() -> adapter(" ", SECRET).fetchSubscriptionSnapshot("sub_1"),
                PaymentApiError.PAYMENT_GATEWAY_MISCONFIGURED);
    }

    @Test
    void mapsApiFailuresToGatewayErrors() {
        routes.put("PUT /preapproval/sub_1", new Response(500, "{\"message\":\"boom\"}"));
        assertGatewayError(() -> adapter.cancelSubscription("sub_1"), PaymentApiError.PAYMENT_GATEWAY_UNAVAILABLE);

        routes.put("GET /preapproval/sub_1", new Response(200, ""));
        assertGatewayError(() -> adapter.fetchSubscriptionSnapshot("sub_1"),
                PaymentApiError.PAYMENT_GATEWAY_EMPTY_RESPONSE);
    }

    @Test
    void cancelsSubscription() {
        routes.put("PUT /preapproval/sub_1", new Response(200, "{\"status\":\"cancelled\"}"));

        adapter.cancelSubscription(" sub_1 ");

        assertThat(receivedBodies.get("PUT /preapproval/sub_1")).contains("cancelled");
    }

    @Test
    void readsSubscriptionSnapshotAndExtendsStalePeriodEnd() {
        routes.put("GET /preapproval/sub_1", new Response(200, SUBSCRIPTION));

        PaymentSubscriptionSnapshot snapshot = adapter.fetchSubscriptionSnapshot("sub_1");

        assertThat(snapshot.subscriptionId()).isEqualTo("sub_1");
        assertThat(snapshot.customerId()).isEqualTo("payer_9");
        assertThat(snapshot.userId()).isEqualTo(USER_ID);
        assertThat(snapshot.planCode()).isEqualTo("BASIC");
        assertThat(snapshot.currentPeriodStart()).isEqualTo(Instant.parse("2026-11-01T15:00:00Z"));
        assertThat(snapshot.currentPeriodEnd()).isEqualTo(Instant.parse("2026-12-01T15:00:00Z"));
    }

    @Test
    void rejectsSubscriptionWithInvalidUserReference() {
        routes.put("GET /preapproval/sub_2", new Response(200, "{\"id\":\"sub_2\",\"external_reference\":\"userId=nope\"}"));

        assertThatThrownBy(() -> adapter.fetchSubscriptionSnapshot("sub_2"))
                .isInstanceOfSatisfying(PaymentWebhookException.class,
                        ex -> assertThat(ex.error()).isEqualTo(PaymentApiError.WEBHOOK_USER_REFERENCE_INVALID));
    }

    @Test
    void mapsSignedPreapprovalWebhook() {
        routes.put("GET /preapproval/sub_1", new Response(200, SUBSCRIPTION));

        PaymentWebhookNotification notification = signedWebhook(
                "{\"id\":\"evt_1\",\"type\":\"subscription_preapproval\",\"data\":{\"id\":\"sub_1\"}}", "sub_1");

        assertThat(notification.eventId()).isEqualTo("evt_1");
        assertThat(notification.subscriptionId()).isEqualTo("sub_1");
        assertThat(notification.userId()).isEqualTo(USER_ID);
        assertThat(notification.resetPeriod()).isFalse();
    }

    @Test
    void mapsApprovedPaymentWebhookAndResetsPeriod() {
        routes.put("GET /v1/payments/PAY1", new Response(200,
                "{\"status\":\"approved\",\"preapproval_id\":\"sub_1\",\"date_approved\":\"2026-11-10T00:00:00Z\"}"));
        routes.put("GET /preapproval/sub_1", new Response(200, SUBSCRIPTION));

        PaymentWebhookNotification notification = signedWebhook(
                "{\"id\":\"evt_2\",\"type\":\"payment\",\"data\":{\"id\":\"PAY1\"}}", "PAY1");

        assertThat(notification.resetPeriod()).isTrue();
        assertThat(notification.currentPeriodStart()).isEqualTo(Instant.parse("2026-11-10T00:00:00Z"));
        assertThat(notification.currentPeriodEnd()).isEqualTo(Instant.parse("2026-12-10T00:00:00Z"));
    }

    @Test
    void returnsBarePaymentNotificationWithoutSubscription() {
        routes.put("GET /v1/payments/PAY2", new Response(200, "{\"status\":\"rejected\"}"));

        PaymentWebhookNotification notification = signedWebhook(
                "{\"id\":\"evt_3\",\"type\":\"payment\",\"data\":{\"id\":\"PAY2\"}}", "PAY2");

        assertThat(notification.subscriptionId()).isNull();
        assertThat(notification.status()).isEqualTo("rejected");
    }

    @Test
    void ignoresWebhooksWithoutResourceOrOfUnknownType() {
        assertThat(signedWebhook("{\"action\":\"test.created\"}", "x").subscriptionId()).isNull();
        assertThat(signedWebhook("{\"id\":\"e\",\"type\":\"plan\",\"data\":{\"id\":\"p\"}}", "p").eventType())
                .isEqualTo("plan");
    }

    @Test
    void rejectsBadSignaturesAndMissingHeaders() {
        String payload = "{\"id\":\"evt\"}";
        assertWebhookError(() -> adapter.parseVerifiedWebhook(payload, "ts=1,v1=deadbeef", "req", "abc"),
                PaymentApiError.WEBHOOK_SIGNATURE_INVALID);
        assertWebhookError(() -> adapter.parseVerifiedWebhook(payload, "ts=1", "req", "abc"),
                PaymentApiError.WEBHOOK_SIGNATURE_INVALID);
        assertWebhookError(() -> adapter.parseVerifiedWebhook(payload, "ts=1,v1=aa", " ", "abc"),
                PaymentApiError.WEBHOOK_REQUEST_ID_REQUIRED);
        assertWebhookError(() -> adapter.parseVerifiedWebhook(payload, "ts=1,v1=aa", "req", null),
                PaymentApiError.WEBHOOK_DATA_ID_REQUIRED);
        assertWebhookError(() -> adapter.parseVerifiedWebhook("{not json", signature("abc", "req"), "req", "ABC"),
                PaymentApiError.WEBHOOK_PAYLOAD_INVALID);
    }

    private PaymentWebhookNotification signedWebhook(String payload, String dataId) {
        return adapter.parseVerifiedWebhook(payload, signature(dataId.toLowerCase(), "req-1"), "req-1", dataId);
    }

    private static String signature(String dataId, String requestId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:1700000000;";
            return "ts=1700000000,v1=" + HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private MercadoPagoPaymentGatewayAdapter adapter(String token, String secret) {
        return new MercadoPagoPaymentGatewayAdapter(
                JsonMapper.builder().build(),
                new MercadoPagoProperties(token, "http://127.0.0.1:" + server.getAddress().getPort(), secret,
                        "http://localhost/success", "http://localhost/cancel")
        );
    }

    private static PaymentPlanDefinition basicPlan() {
        return new PaymentPlanDefinition(SubscriptionPlanCode.BASIC, "Básico", "", 200, 1990, "pen", 8, null);
    }

    private static void assertGatewayError(Runnable call, PaymentApiError expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(PaymentGatewayException.class,
                ex -> assertThat(ex.error()).isEqualTo(expected));
    }

    private static void assertWebhookError(Runnable call, PaymentApiError expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(PaymentWebhookException.class,
                ex -> assertThat(ex.error()).isEqualTo(expected));
    }
}
