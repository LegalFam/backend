package com.legalfam.backend.chat.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.application.dto.ChatPreviousMessage;
import com.legalfam.backend.chat.domain.exception.ChatApiError;
import com.legalfam.backend.chat.domain.exception.ChatUpstreamException;
import com.legalfam.backend.chat.domain.model.ChatLanguage;
import com.legalfam.backend.chat.infrastructure.config.N8nProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class N8nWebhookClientHttpTest {

    private static final UUID SESSION = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private HttpServer server;
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<String> body = new AtomicReference<>("{}");
    private final AtomicReference<Long> delayMs = new AtomicReference<>(0L);
    private final AtomicReference<String> received = new AtomicReference<>();
    private final AtomicReference<String> token = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            token.set(exchange.getRequestHeaders().getFirst("X-N8N-Token"));
            try {
                Thread.sleep(delayMs.get());
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length == 0 ? -1 : bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsHistoryLanguageAndTokenToTheWebhook() {
        body.set("{\"message\":\"ok\"}");

        client("secreto").sendMessage("¿Pensión?", SESSION, List.of(
                new ChatPreviousMessage("USER", "antes", Instant.parse("2026-11-15T10:00:00Z")),
                new ChatPreviousMessage("ASSISTANT", "respuesta", null)
        ), null);

        assertThat(token.get()).isEqualTo("secreto");
        assertThat(received.get())
                .contains("\"session_id\":\"" + SESSION + "\"")
                .contains("\"language\":\"es\"")
                .contains("\"created_at\":\"2026-11-15T10:00:00Z\"")
                .contains("\"role\":\"ASSISTANT\"");
    }

    @Test
    void omitsTheTokenHeaderWhenNoTokenIsConfigured() {
        body.set("{\"message\":\"ok\"}");

        client("").sendMessage("hola", SESSION, null, ChatLanguage.ES);

        assertThat(token.get()).isNull();
        assertThat(received.get()).contains("\"previous_messages\":[]");
    }

    @Test
    void wrapsPlainTextAnswersAndReadsLooseMetadata() {
        body.set("Respuesta en texto plano");
        assertThat(send().message()).isEqualTo("Respuesta en texto plano");

        body.set("""
                {"message":"ok","citations":{"file_name":"Código Civil","locator":"Art. 472"},
                 "specialistSupportRecommended":"TRUE","nextSteps":["  Ir al juzgado ", "", 3],
                 "citationSupportStatus":"weak"}
                """);
        ChatAssistantGatewayResponse response = send();
        assertThat(response.citations()).singleElement()
                .satisfies(citation -> assertThat(citation.sourceLocator()).isEqualTo("Art. 472"));
        assertThat(response.metadata().specialistSupportRecommended()).isTrue();
        assertThat(response.metadata().nextSteps()).containsExactly("Ir al juzgado", "3");
        assertThat(response.metadata().citationSupportStatus()).isEqualTo("WEAK");

        body.set("""
                {"message":"ok","citations":[{"locator":"sin datos"}],"specialistSupportRecommended":"tal vez",
                 "citationSupportStatus":"raro"}
                """);
        response = send();
        assertThat(response.citations()).isEmpty();
        assertThat(response.metadata().specialistSupportRecommended()).isNull();
        assertThat(response.metadata().citationSupportStatus()).isNull();

        body.set("{\"message\":\"ok\",\"citations\":\"texto\",\"specialistSupportRecommended\":\"false\"}");
        response = send();
        assertThat(response.citations()).isEmpty();
        assertThat(response.metadata().specialistSupportRecommended()).isFalse();
        assertThat(response.metadata().citationSupportStatus()).isEqualTo("NONE");
    }

    @Test
    void mapsUpstreamStatusCodesToChatErrors() {
        assertStatus(500, "{\"detail\":{\"code\":\"agent_validation_failed\",\"message\":\"x\"}}",
                ChatApiError.AGENT_VALIDATION_FAILED);
        assertStatus(500, "{\"detail\":\"falló el flujo\"}", ChatApiError.UPSTREAM_ERROR);
        assertStatus(504, "", ChatApiError.UPSTREAM_TIMEOUT);
        assertStatus(502, "<html>bad gateway</html>", ChatApiError.UPSTREAM_UNAVAILABLE);
        assertStatus(422, "{}", ChatApiError.AGENT_VALIDATION_FAILED);
        assertStatus(404, "x".repeat(600), ChatApiError.UPSTREAM_ERROR);
    }

    @Test
    void reportsAnEmptySuccessBody() {
        body.set("");

        assertError(this::send, ChatApiError.UPSTREAM_EMPTY_RESPONSE);
    }

    @Test
    void reportsTimeoutsAndUnreachableHosts() throws IOException {
        delayMs.set(1500L);
        assertError(this::send, ChatApiError.UPSTREAM_TIMEOUT);

        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        N8nWebhookClient unreachable = new N8nWebhookClient(new ObjectMapper(),
                new N8nProperties("http://127.0.0.1:" + closedPort + "/webhook", null, "", 1000));
        assertError(() -> unreachable.sendMessage("hola", SESSION, List.of(), ChatLanguage.ES),
                ChatApiError.UPSTREAM_UNAVAILABLE);
    }

    @Test
    void rejectsMissingOrInvalidWebhookUrls() {
        assertError(() -> new N8nWebhookClient(new ObjectMapper(), new N8nProperties(" ", null, null, null))
                .sendMessage("hola", SESSION, List.of(), ChatLanguage.ES), ChatApiError.UPSTREAM_NOT_CONFIGURED);
        assertError(() -> new N8nWebhookClient(new ObjectMapper(), new N8nProperties("http://bad host/x", null, null, 1000))
                .sendMessage("hola", SESSION, List.of(), ChatLanguage.ES), ChatApiError.UPSTREAM_NOT_CONFIGURED);
    }

    @Test
    void failsOnStreamsWithOnlyErrorsNoFinalPayloadOrBrokenJson() {
        body.set("{\"type\":\"error\",\"content\":\"boom\",\"metadata\":{\"nodeName\":\"Parser\"}}"
                + "{\"type\":\"error\",\"metadata\":{}}");
        assertError(this::send, ChatApiError.UPSTREAM_ERROR);

        body.set("{\"type\":\"begin\",\"metadata\":{\"nodeName\":\"Otro nodo\"}}");
        assertError(this::send, ChatApiError.UPSTREAM_EMPTY_RESPONSE);

        body.set("{\"type\":\"item\",\"content\":{\"a\":");
        assertError(this::send, ChatApiError.UPSTREAM_ERROR);
    }

    @Test
    void keepsTheFinalPayloadDespiteTrailingGarbageAndFailingDraftListener() {
        body.set("{\"type\":\"item\",\"content\":\"Hola\",\"metadata\":{\"nodeName\":\"XAI Agent\"}}"
                + "{\"type\":\"item\",\"content\":{\"message\":\"Final\"},\"metadata\":{\"nodeName\":\"Respond to Webhook\"}}"
                + "{\"type\":");

        ChatAssistantGatewayResponse response = client("").sendMessage("hola", SESSION, List.of(), ChatLanguage.ES,
                draft -> {
                    throw new IllegalStateException("cliente desconectado");
                });

        assertThat(response.message()).isEqualTo("Final");
    }

    private ChatAssistantGatewayResponse send() {
        return client("").sendMessage("hola", SESSION, List.of(), ChatLanguage.ES);
    }

    private N8nWebhookClient client(String authToken) {
        return new N8nWebhookClient(new ObjectMapper(), new N8nProperties(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook", "X-N8N-Token", authToken, 1000));
    }

    private void assertStatus(int code, String response, ChatApiError expected) {
        status.set(code);
        body.set(response);
        assertError(this::send, expected);
    }

    private static void assertError(Runnable call, ChatApiError expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ChatUpstreamException.class,
                ex -> assertThat(ex.error()).isEqualTo(expected));
    }
}
