package com.legalfam.backend.chat.infrastructure.adapter.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.domain.model.ChatLanguage;
import com.legalfam.backend.chat.infrastructure.config.N8nProperties;
import java.lang.reflect.Method;
import java.util.List;
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
        mapResponse = N8nWebhookClient.class.getDeclaredMethod("mapResponse", JsonNode.class, ChatLanguage.class);
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

    @Test
    void mapResponseReadsLocalizedFieldsWhenLanguageIsNotSpanish() throws Exception {
        ChatAssistantGatewayResponse response = map("""
                {
                  "message": "respuesta en espanol",
                  "message_localized": "kutichiy runasimipi",
                  "user_message_es": "consulta traducida",
                  "nextSteps": ["Reune constancias."],
                  "nextSteps_localized": ["Qillqakunata huñuy."],
                  "citations": [
                    {
                      "file_name": "Codigo Civil",
                      "summary_snippet": "resumen en espanol",
                      "summary_snippet_localized": "pisi willakuy",
                      "original_snippet": "Articulo 472.- Se considera alimentos...",
                      "file_url": "https://example.test/cc"
                    }
                  ],
                  "agentTokenCost": 3
                }
                """, ChatLanguage.QU);

        assertEquals("respuesta en espanol", response.message());
        assertEquals("kutichiy runasimipi", response.messageLocalized());
        assertEquals("consulta traducida", response.userMessageTranslated());
        assertEquals(List.of("Qillqakunata huñuy."), response.metadata().nextStepsLocalized());
        assertEquals("pisi willakuy", response.citations().get(0).sourceSnippetLocalized());
        // El pasaje literal de la norma llega sin traducir, que es la unica forma de poder
        // contrastar la cita contra la fuente.
        assertEquals(
                "Articulo 472.- Se considera alimentos...",
                response.citations().get(0).sourceOriginalSnippet()
        );
    }

    @Test
    void mapResponseIgnoresLocalizedFieldsWhenLanguageIsSpanish() throws Exception {
        ChatAssistantGatewayResponse response = map("""
                {
                  "message": "respuesta en espanol",
                  "message_localized": "no deberia usarse",
                  "user_message_es": "tampoco",
                  "nextSteps_localized": ["ni esto"],
                  "citations": []
                }
                """);

        assertNull(response.messageLocalized());
        assertNull(response.userMessageTranslated());
        assertEquals(List.of(), response.metadata().nextStepsLocalized());
    }

    @Test
    void mapResponseSurvivesMissingTranslationOnNonSpanishConversation() throws Exception {
        // Si la traduccion falla aguas arriba, el turno sigue siendo utilizable: se entrega
        // el espanol y el frontend lo muestra tal cual.
        ChatAssistantGatewayResponse response = map("""
                {
                  "message": "respuesta en espanol",
                  "citations": []
                }
                """, ChatLanguage.AY);

        assertEquals("respuesta en espanol", response.message());
        assertNull(response.messageLocalized());
    }

    /** El flujo leyo aymara donde la interfaz habia pedido quechua: manda lo que leyo. */
    @Test
    void mapResponseTakesLanguageFromTheFlowNotFromTheRequest() throws Exception {
        ChatAssistantGatewayResponse response = map("""
                {
                  "message": "respuesta",
                  "message_localized": "jaysawi",
                  "user_message_es": "que puedo hacer",
                  "language": "ay",
                  "language_requested": "qu",
                  "citations": []
                }
                """, ChatLanguage.QU);

        assertEquals("ay", response.languageDetected());
        assertEquals("jaysawi", response.messageLocalized());
        assertEquals("que puedo hacer", response.userMessageTranslated());
    }

    /**
     * Se pidio quechua pero se escribio en espanol: el flujo responde en espanol y no manda
     * campos localizados, asi que tampoco hay que ir a buscarlos.
     */
    @Test
    void mapResponseIgnoresLocalizedFieldsWhenTheFlowAnsweredInSpanish() throws Exception {
        ChatAssistantGatewayResponse response = map("""
                {
                  "message": "respuesta",
                  "language": "es",
                  "language_requested": "qu",
                  "citations": []
                }
                """, ChatLanguage.QU);

        assertEquals("es", response.languageDetected());
        assertNull(response.messageLocalized());
        assertNull(response.userMessageTranslated());
    }

    /** Flujo anterior a la deteccion: sin `language`, se respeta el idioma solicitado. */
    @Test
    void mapResponseFallsBackToRequestedLanguageWhenFlowOmitsIt() throws Exception {
        ChatAssistantGatewayResponse response = map("""
                {
                  "message": "respuesta",
                  "message_localized": "kutichiy",
                  "citations": []
                }
                """, ChatLanguage.QU);

        assertEquals("qu", response.languageDetected());
        assertEquals("kutichiy", response.messageLocalized());
    }

    private ChatAssistantGatewayResponse map(String responseBody) throws Exception {
        return map(responseBody, ChatLanguage.ES);
    }

    private ChatAssistantGatewayResponse map(String responseBody, ChatLanguage language) throws Exception {
        JsonNode root = (JsonNode) parseResponseBody.invoke(client, responseBody);
        return (ChatAssistantGatewayResponse) mapResponse.invoke(client, root, language);
    }
}
