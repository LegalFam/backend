package com.legalfam.backend.chat.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.infrastructure.config.N8nProperties;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class N8nWebhookClientTest {

    private N8nWebhookClient client;
    private Method parseResponseBody;
    private Method mapResponse;

    @BeforeEach
    void setUp() throws Exception {
        client = new N8nWebhookClient(
                new ObjectMapper(),
                new N8nProperties("http://localhost/webhook", "X-N8N-Token", "", 1000)
        );
        parseResponseBody = N8nWebhookClient.class.getDeclaredMethod("parseResponseBody", String.class);
        parseResponseBody.setAccessible(true);
        mapResponse = N8nWebhookClient.class.getDeclaredMethod("mapResponse", JsonNode.class);
        mapResponse.setAccessible(true);
    }

    @Test
    void mapResponseReadsExplicitParserOnlyTokenCost() throws Exception {
        ChatAssistantGatewayResponse response = map("""
                {
                  "message": "respuesta",
                  "citations": [],
                  "citationSupportStatus": null,
                  "agentTokenCost": 1
                }
                """);

        assertEquals(1, response.metadata().agentTokenCost());
    }

    @Test
    void mapResponseReadsExplicitRagTokenCostFromString() throws Exception {
        ChatAssistantGatewayResponse response = map("""
                {
                  "message": "respuesta",
                  "citations": [],
                  "citationSupportStatus": "NONE",
                  "agentTokenCost": "3"
                }
                """);

        assertEquals(3, response.metadata().agentTokenCost());
    }

    @Test
    void mapResponseDefaultsToParserOnlyCostWhenBillingMetadataIsInvalid() throws Exception {
        ChatAssistantGatewayResponse response = map("""
                {
                  "message": "respuesta",
                  "citations": [],
                  "citationSupportStatus": null,
                  "agentTokenCost": "invalid"
                }
                """);

        assertEquals(1, response.metadata().agentTokenCost());
    }

    @Test
    void mapResponseDefaultsToParserOnlyCostWhenBillingMetadataIsMissing() throws Exception {
        ChatAssistantGatewayResponse response = map("""
                {
                  "message": "respuesta",
                  "citations": [],
                  "citationSupportStatus": "GOOD"
                }
                """);

        assertEquals(1, response.metadata().agentTokenCost());
    }

    @Test
    void mapResponseInfersGoodCitationSupportWhenValidCitationsExist() throws Exception {
        ChatAssistantGatewayResponse response = map("""
                {
                  "message": "respuesta",
                  "citations": [
                    {
                      "file_name": "Codigo Civil",
                      "summary_snippet": "Articulo relevante",
                      "original_snippet": "El demandante goza de Auxilio Judicial",
                      "file_url": "https://example.com/codigo"
                    },
                    {
                      "locator": "Art. 562"
                    }
                  ]
                }
                """);

        assertEquals("GOOD", response.metadata().citationSupportStatus());
        assertEquals(1, response.citations().size());
        assertEquals("Codigo Civil", response.citations().getFirst().sourceTitle());
        assertEquals("https://example.com/codigo", response.citations().getFirst().sourceUrl());
        assertEquals("Articulo relevante", response.citations().getFirst().sourceSnippet());
        assertEquals(
                "El demandante goza de Auxilio Judicial",
                response.citations().getFirst().sourceOriginalSnippet()
        );
    }

    @Test
    void mapResponseKeepsCitationsWithoutSourceUrl() throws Exception {
        // No todo documento del corpus tiene fuente publica. Descartar la cita por eso
        // dejaba la respuesta entera sin fuentes, que es peor que una cita sin enlace.
        ChatAssistantGatewayResponse response = map("""
                {
                  "message": "respuesta",
                  "citations": [
                    {
                      "summary_snippet": "Exoneracion de tasas judiciales",
                      "original_snippet": "El demandante se encuentra exonerado",
                      "locator": "Art. 562"
                    }
                  ]
                }
                """);

        assertEquals(1, response.citations().size());
        assertNull(response.citations().getFirst().sourceUrl());
        assertEquals("Art. 562", response.citations().getFirst().sourceLocator());
    }

    @Test
    void mapResponseKeepsCitationsWithoutOriginalSnippet() throws Exception {
        // Una cita sin pasaje literal sigue siendo valida: pierde el contraste con el
        // resumen, no la fuente.
        ChatAssistantGatewayResponse response = map("""
                {
                  "message": "respuesta",
                  "citations": [
                    {
                      "file_name": "Codigo Civil",
                      "summary_snippet": "Articulo relevante",
                      "file_url": "https://example.com/codigo"
                    }
                  ]
                }
                """);

        assertEquals(1, response.citations().size());
        assertNull(response.citations().getFirst().sourceOriginalSnippet());
    }

    @Test
    void mapResponseIgnoresInvalidCitationSupportStatus() throws Exception {
        ChatAssistantGatewayResponse response = map("""
                {
                  "message": "respuesta",
                  "citations": [],
                  "citationSupportStatus": "UNKNOWN"
                }
                """);

        assertEquals(null, response.metadata().citationSupportStatus());
    }

    @Test
    void parseResponseBodyTreatsPlainTextAsAssistantMessage() throws Exception {
        ChatAssistantGatewayResponse response = map("respuesta en texto plano");

        assertEquals("respuesta en texto plano", response.message());
        assertEquals("NONE", response.metadata().citationSupportStatus());
        assertEquals(1, response.metadata().agentTokenCost());
    }

    private ChatAssistantGatewayResponse map(String responseBody) throws Exception {
        JsonNode root = (JsonNode) parseResponseBody.invoke(client, responseBody);
        return (ChatAssistantGatewayResponse) mapResponse.invoke(client, root);
    }
}
