package com.legalfam.backend.chat.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.domain.exception.ChatApiError;
import com.legalfam.backend.chat.domain.exception.ChatUpstreamException;
import com.legalfam.backend.chat.domain.model.ChatLanguage;
import com.legalfam.backend.chat.infrastructure.config.N8nProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Contra un servidor HTTP real que escribe por partes, como el webhook de n8n en modo
 * streaming: asi se prueba que los borradores salen mientras la respuesta sigue abierta.
 */
class N8nWebhookClientStreamingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FINAL_PAYLOAD = """
            {"message":"Respuesta final.","citations":[{"file_name":"Codigo Civil","summary_snippet":"Resumen","locator":"Art. 481"}],"citationSupportStatus":"GOOD","confidenceStatus":"HIGH","nextSteps":["Reunir documentos"],"agentTokenCost":3,"language":"es"}""";

    private HttpServer server;
    private final AtomicReference<List<Part>> script = new AtomicReference<>(List.of());
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedToken = new AtomicReference<>();
    private final AtomicLong finishedWritingAt = new AtomicLong();
    private N8nWebhookClient client;

    private record Part(String text, long pauseMsAfter) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook/chat-process", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-N8N-Token"));
            List<Part> parts = script.get();
            int status = parts.isEmpty() ? 200 : parts.getFirst().text().startsWith("HTTP ")
                    ? Integer.parseInt(parts.getFirst().text().substring(5))
                    : 200;
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (Part part : parts) {
                    if (part.text().startsWith("HTTP ")) {
                        continue;
                    }
                    out.write(part.text().getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    sleep(part.pauseMsAfter());
                }
                finishedWritingAt.set(System.nanoTime());
            }
        });
        server.start();
        client = new N8nWebhookClient(
                new ObjectMapper(),
                new N8nProperties(
                        "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook/chat-process",
                        "X-N8N-Token",
                        "secret",
                        5000
                )
        );
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void streamsTheAnswerBeforeTheFinalPayloadArrives() {
        script.set(List.of(
                part(chunk("begin", "Parser Agent", null), 0),
                part(chunk("end", "Parser Agent", null), 0),
                part(chunk("begin", "XAI Agent", null), 0),
                part(chunk("item", "XAI Agent", "Con lo que cuentas, "), 50),
                part(chunk("item", "XAI Agent", "el juez fija la pension."), 300),
                part(chunk("item", "XAI Agent", "\n<<<LEGALFAM_"), 0),
                part(chunk("item", "XAI Agent", "META>>>\n{\"citations\":[]}"), 0),
                part(chunk("end", "XAI Agent", null), 0),
                part(chunk("begin", "Respond to Webhook", null), 0),
                part(chunk("item", "Respond to Webhook", FINAL_PAYLOAD), 0),
                part(chunk("end", "Respond to Webhook", null), 0)
        ));
        List<String> drafts = new CopyOnWriteArrayList<>();
        List<Long> draftTimes = new CopyOnWriteArrayList<>();

        ChatAssistantGatewayResponse response = client.sendMessage(
                "hola", UUID.randomUUID(), List.of(), ChatLanguage.ES,
                text -> {
                    drafts.add(text);
                    draftTimes.add(System.nanoTime());
                }
        );

        assertEquals("Respuesta final.", response.message());
        assertEquals("GOOD", response.metadata().citationSupportStatus());
        assertEquals(3, response.metadata().agentTokenCost());
        assertEquals("Art. 481", response.citations().getFirst().sourceLocator());
        assertEquals(List.of("Con lo que cuentas,", "Con lo que cuentas, el juez fija la pension."), drafts);
        assertTrue(draftTimes.getFirst() < finishedWritingAt.get(), "el primer borrador debe salir antes del final");
        assertTrue(drafts.stream().noneMatch(text -> text.contains("<<<") || text.contains("citations")));
    }

    @Test
    void sendsTheRequestWithAuthAndConversation() throws Exception {
        script.set(List.of(part(FINAL_PAYLOAD, 0)));

        client.sendMessage("hola", UUID.randomUUID(), List.of(), ChatLanguage.ES, text -> {
        });

        assertEquals("secret", receivedToken.get());
        assertEquals("hola", MAPPER.readTree(receivedBody.get()).get("message").asString());
    }

    @Test
    void keepsWorkingWithAWorkflowThatAnswersWithASingleJson() {
        script.set(List.of(part(FINAL_PAYLOAD, 0)));
        List<String> drafts = new CopyOnWriteArrayList<>();

        ChatAssistantGatewayResponse response = client.sendMessage(
                "hola", UUID.randomUUID(), List.of(), ChatLanguage.ES, drafts::add
        );

        assertEquals("Respuesta final.", response.message());
        assertTrue(drafts.isEmpty());
    }

    @Test
    void keepsWorkingWithAPlainTextAnswer() {
        script.set(List.of(part("Solo texto", 0)));

        ChatAssistantGatewayResponse response = client.sendMessage("hola", UUID.randomUUID(), List.of(), ChatLanguage.ES);

        assertEquals("Solo texto", response.message());
    }

    @Test
    void aRetriedAgentDiscardsWhatItHadStreamed() {
        script.set(List.of(
                part(chunk("begin", "XAI Agent", null), 0),
                part(chunk("item", "XAI Agent", "Primer intento"), 0),
                part(chunk("error", "XAI Agent", "503 overloaded"), 0),
                part(chunk("begin", "XAI Agent", null), 0),
                part(chunk("item", "XAI Agent", "Segundo"), 0),
                part(chunk("item", "Respond to Webhook", FINAL_PAYLOAD), 0)
        ));
        List<String> drafts = new CopyOnWriteArrayList<>();

        ChatAssistantGatewayResponse response = client.sendMessage(
                "hola", UUID.randomUUID(), List.of(), ChatLanguage.ES, drafts::add
        );

        assertEquals(List.of("Primer intento", "", "Segundo"), drafts);
        assertEquals("Respuesta final.", response.message());
    }

    @Test
    void aStreamThatEndsInErrorWithoutAFinalPayloadFails() {
        script.set(List.of(
                part(chunk("begin", "XAI Agent", null), 0),
                part(chunk("error", "XAI Agent", "model not found"), 0)
        ));

        ChatUpstreamException error = assertThrows(ChatUpstreamException.class, () ->
                client.sendMessage("hola", UUID.randomUUID(), List.of(), ChatLanguage.ES, text -> {
                }));

        assertEquals(ChatApiError.UPSTREAM_ERROR, error.error());
    }

    @Test
    void aStreamWithoutFinalPayloadIsAnEmptyResponse() {
        script.set(List.of(part(chunk("begin", "XAI Agent", null), 0), part(chunk("end", "XAI Agent", null), 0)));

        ChatUpstreamException error = assertThrows(ChatUpstreamException.class, () ->
                client.sendMessage("hola", UUID.randomUUID(), List.of(), ChatLanguage.ES));

        assertEquals(ChatApiError.UPSTREAM_EMPTY_RESPONSE, error.error());
    }

    @Test
    void aFailingDraftListenerDoesNotCostTheAnswer() {
        script.set(List.of(
                part(chunk("item", "XAI Agent", "Texto"), 0),
                part(chunk("item", "Respond to Webhook", FINAL_PAYLOAD), 0)
        ));

        ChatAssistantGatewayResponse response = client.sendMessage(
                "hola", UUID.randomUUID(), List.of(), ChatLanguage.ES,
                text -> {
                    throw new IllegalStateException("sse caido");
                }
        );

        assertEquals("Respuesta final.", response.message());
    }

    @Test
    void chunksWrittenBackToBackWithoutSeparatorsAreRead() {
        script.set(List.of(part(
                chunk("item", "XAI Agent", "Hola")
                        + chunk("item", "XAI Agent", " mundo")
                        + chunk("item", "Respond to Webhook", FINAL_PAYLOAD),
                0
        )));
        List<String> drafts = new CopyOnWriteArrayList<>();

        ChatAssistantGatewayResponse response = client.sendMessage(
                "hola", UUID.randomUUID(), List.of(), ChatLanguage.ES, drafts::add
        );

        assertEquals("Respuesta final.", response.message());
        assertFalse(drafts.isEmpty());
        assertEquals("Hola mundo", drafts.getLast());
    }

    @Test
    void serverErrorsStillMapToUpstreamErrors() {
        script.set(List.of(part("HTTP 500", 0), part("{\"message\":\"boom\"}", 0)));

        ChatUpstreamException error = assertThrows(ChatUpstreamException.class, () ->
                client.sendMessage("hola", UUID.randomUUID(), List.of(), ChatLanguage.ES));

        assertEquals(ChatApiError.UPSTREAM_ERROR.code(), error.error().code());
    }

    private static Part part(String text, long pauseMsAfter) {
        return new Part(text, pauseMsAfter);
    }

    /** Igual que n8n: JSON.stringify de {type, content, metadata}, una linea por chunk. */
    private static String chunk(String type, String node, String content) {
        ObjectNode chunk = MAPPER.createObjectNode();
        chunk.put("type", type);
        if (content != null) {
            chunk.put("content", content);
        }
        ObjectNode metadata = chunk.putObject("metadata");
        metadata.put("nodeId", UUID.randomUUID().toString());
        metadata.put("nodeName", node);
        metadata.put("itemIndex", 0);
        metadata.put("runIndex", 0);
        metadata.put("timestamp", System.currentTimeMillis());
        return MAPPER.writeValueAsString(chunk) + "\n";
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
