package com.legalfam.backend.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.chat.application.dto.ChatAssistantGatewayResponse;
import com.legalfam.backend.chat.application.dto.ChatAssistantMetadata;
import com.legalfam.backend.chat.application.event.ChatAssistantDeliveryQueuedEvent;
import com.legalfam.backend.chat.application.port.out.IChatOutboxPort;
import com.legalfam.backend.chat.application.port.out.IChatPersistencePort;
import com.legalfam.backend.chat.application.port.out.IChatTokenPort;
import com.legalfam.backend.chat.domain.model.ChatLanguage;
import com.legalfam.backend.chat.domain.model.ChatMessage;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessing;
import com.legalfam.backend.chat.domain.model.ChatMessageProcessingStatus;
import com.legalfam.backend.chat.domain.model.ChatMessageRole;
import com.legalfam.backend.chat.domain.model.ChatSession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatAssistantPersistenceServiceTest {

    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock
    private IChatPersistencePort chatPersistencePort;

    @Mock
    private IChatOutboxPort chatOutboxPort;

    @Mock
    private IChatTokenPort chatTokenPort;

    @InjectMocks
    private ChatAssistantPersistenceService chatAssistantPersistenceService;

    @Test
    void persistAssistantMessageConsumesRagTokensForAssistantResult() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        ChatSession session = ChatSession.restore(sessionId, userId, null, Instant.now(), Instant.now());
        ChatMessage userMessage = ChatMessage.userMessage(sessionId, "hola", Instant.now());
        ChatAssistantMetadata metadata = new ChatAssistantMetadata(
                "MEDIUM",
                null,
                List.of(),
                List.of(),
                false,
                "GOOD",
                3
        );

        when(chatPersistencePort.findSessionById(sessionId)).thenReturn(Optional.of(session));
        when(chatPersistencePort.saveMessage(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatPersistencePort.findMessageById(userMessageId)).thenReturn(Optional.of(userMessage));
        when(chatPersistencePort.findMessageProcessingByUserMessageIdForUpdate(userMessageId))
                .thenReturn(Optional.of(processing(userMessageId)));
        when(chatPersistencePort.saveMessageProcessing(any(ChatMessageProcessing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(chatPersistencePort.saveSession(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        chatAssistantPersistenceService.persistAssistantMessage(
                sessionId,
                userMessageId,
                new ChatAssistantGatewayResponse("respuesta", null, null, null, List.of(), metadata),
                ChatLanguage.ES
        );

        verify(chatTokenPort).consumeChatTokensForAssistantResult(userId, userMessageId, 3);
    }

    /**
     * La interfaz pidio quechua pero la persona escribio en aymara. Manda lo que el flujo
     * leyo del texto: la respuesta se guarda en aymara, y el quechua queda anotado al lado
     * para que el frontend pueda explicar el cambio.
     */
    @Test
    void persistAssistantMessageUsesDetectedLanguageOverRequestedOne() {
        UUID userMessageId = UUID.randomUUID();
        ChatMessage userMessage = ChatMessage.userMessage(
                SESSION_ID,
                "kunas lurasmaxa wawanakajata",
                ChatLanguage.QU,
                Instant.now()
        );

        List<ChatMessage> saved = arrangeSuccessfulPersistence(userMessageId, userMessage);

        chatAssistantPersistenceService.persistAssistantMessage(
                SESSION_ID,
                userMessageId,
                new ChatAssistantGatewayResponse(
                        "respuesta en espanol",
                        "respuesta en aymara",
                        "que puedo hacer por mis hijos",
                        "ay",
                        List.of(),
                        emptyMetadata()
                ),
                ChatLanguage.QU
        );

        ChatMessage assistant = saved.stream()
                .filter(message -> message.getRole() == ChatMessageRole.ASSISTANT)
                .findFirst()
                .orElseThrow();
        assertEquals(ChatLanguage.AY, assistant.getLanguage());
        assertEquals(ChatLanguage.QU, assistant.getLanguageRequested());
        assertEquals("respuesta en aymara", assistant.getContentLocalized());

        // El mensaje del usuario se habia guardado como quechua: se corrige, y su espanol
        // canonico pasa a `content` para que el historial no viaje en idiomas mezclados.
        assertEquals(ChatLanguage.AY, userMessage.getLanguage());
        assertEquals(ChatLanguage.QU, userMessage.getLanguageRequested());
        assertEquals("que puedo hacer por mis hijos", userMessage.getContent());
        assertEquals("kunas lurasmaxa wawanakajata", userMessage.getContentLocalized());
    }

    /**
     * El caso central del producto: la app arranca en espanol y quien no encuentra el
     * selector de idioma escribe igual en quechua. Nadie pidio quechua en ningun momento y
     * aun asi la respuesta sale en quechua, que es lo unico que hace util la funcion para la
     * persona a la que esta dirigida.
     */
    @Test
    void persistAssistantMessageAnswersInQuechuaWhenAppWasStillInSpanish() {
        UUID userMessageId = UUID.randomUUID();
        ChatMessage userMessage = ChatMessage.userMessage(
                SESSION_ID,
                "mikuy qullqita munani",
                ChatLanguage.ES,
                Instant.now()
        );

        List<ChatMessage> saved = arrangeSuccessfulPersistence(userMessageId, userMessage);

        chatAssistantPersistenceService.persistAssistantMessage(
                SESSION_ID,
                userMessageId,
                new ChatAssistantGatewayResponse(
                        "respuesta en espanol",
                        "kutichiy runasimipi",
                        "quiero pension de alimentos",
                        "qu",
                        List.of(),
                        emptyMetadata()
                ),
                ChatLanguage.ES
        );

        ChatMessage assistant = saved.stream()
                .filter(message -> message.getRole() == ChatMessageRole.ASSISTANT)
                .findFirst()
                .orElseThrow();
        assertEquals(ChatLanguage.QU, assistant.getLanguage());
        assertEquals(ChatLanguage.ES, assistant.getLanguageRequested());
        assertEquals("kutichiy runasimipi", assistant.getContentLocalized());

        // Su turno se habia guardado como espanol, con `content_localized` vacio. Ahora el
        // espanol canonico es la traduccion y lo que escribio pasa a ser la version en su
        // lengua, que es la que vera en pantalla.
        assertEquals(ChatLanguage.QU, userMessage.getLanguage());
        assertEquals(ChatLanguage.ES, userMessage.getLanguageRequested());
        assertEquals("quiero pension de alimentos", userMessage.getContent());
        assertEquals("mikuy qullqita munani", userMessage.getContentLocalized());
    }

    /**
     * Con la interfaz en quechua se escribio en espanol. Se responde en espanol y no queda
     * nada que conmutar: el original ya ES la version canonica.
     */
    @Test
    void persistAssistantMessageDropsLocalizationWhenUserWroteInSpanish() {
        UUID userMessageId = UUID.randomUUID();
        ChatMessage userMessage = ChatMessage.userMessage(
                SESSION_ID,
                "quiero pension de alimentos",
                ChatLanguage.QU,
                Instant.now()
        );

        List<ChatMessage> saved = arrangeSuccessfulPersistence(userMessageId, userMessage);

        chatAssistantPersistenceService.persistAssistantMessage(
                SESSION_ID,
                userMessageId,
                new ChatAssistantGatewayResponse("respuesta", null, null, "es", List.of(), emptyMetadata()),
                ChatLanguage.QU
        );

        ChatMessage assistant = saved.stream()
                .filter(message -> message.getRole() == ChatMessageRole.ASSISTANT)
                .findFirst()
                .orElseThrow();
        assertEquals(ChatLanguage.ES, assistant.getLanguage());
        assertEquals(ChatLanguage.QU, assistant.getLanguageRequested());
        assertNull(assistant.getContentLocalized());

        assertEquals(ChatLanguage.ES, userMessage.getLanguage());
        assertEquals("quiero pension de alimentos", userMessage.getContent());
        assertNull(userMessage.getContentLocalized());
    }

    /**
     * Un flujo n8n anterior a la deteccion no devuelve idioma. Se respeta el solicitado y
     * nada cambia respecto del comportamiento previo, empezando por no inventar un aviso.
     */
    @Test
    void persistAssistantMessageFallsBackToRequestedLanguageWhenFlowReturnsNone() {
        UUID userMessageId = UUID.randomUUID();
        ChatMessage userMessage = ChatMessage.userMessage(
                SESSION_ID,
                "mikuy qullqita munani",
                ChatLanguage.QU,
                Instant.now()
        );

        List<ChatMessage> saved = arrangeSuccessfulPersistence(userMessageId, userMessage);

        chatAssistantPersistenceService.persistAssistantMessage(
                SESSION_ID,
                userMessageId,
                new ChatAssistantGatewayResponse(
                        "respuesta",
                        "kutichiy",
                        "quiero pension de alimentos",
                        null,
                        List.of(),
                        emptyMetadata()
                ),
                ChatLanguage.QU
        );

        ChatMessage assistant = saved.stream()
                .filter(message -> message.getRole() == ChatMessageRole.ASSISTANT)
                .findFirst()
                .orElseThrow();
        assertEquals(ChatLanguage.QU, assistant.getLanguage());
        assertNull(assistant.getLanguageRequested());
        assertEquals(ChatLanguage.QU, userMessage.getLanguage());
        assertNull(userMessage.getLanguageRequested());
    }

    @Test
    void persistAssistantFailureEnqueuesErrorInOutbox() {
        UUID userMessageId = UUID.randomUUID();
        ChatMessage userMessage = ChatMessage.userMessage(SESSION_ID, "hola", Instant.now());
        List<ChatMessage> saved = arrangeSuccessfulPersistence(userMessageId, userMessage);

        chatAssistantPersistenceService.persistAssistantFailure(
                SESSION_ID,
                userMessageId,
                "upstream_timeout",
                "Assistant service timed out"
        );

        ArgumentCaptor<ChatAssistantDeliveryQueuedEvent> delivery =
                ArgumentCaptor.forClass(ChatAssistantDeliveryQueuedEvent.class);
        verify(chatOutboxPort).enqueueAssistantDelivery(delivery.capture());
        ChatMessage failure = saved.getFirst();
        assertEquals(ChatMessageRole.SYSTEM, failure.getRole());
        assertEquals(failure.getId(), delivery.getValue().assistantMessageId());
        assertNull(delivery.getValue().event());
        assertEquals("upstream_timeout", delivery.getValue().error().errorCode());
        assertEquals("PENDING", delivery.getValue().error().receiptStatus());
    }

    /** Deja el servicio listo para persistir y devuelve la lista donde caen los guardados. */
    private List<ChatMessage> arrangeSuccessfulPersistence(UUID userMessageId, ChatMessage userMessage) {
        List<ChatMessage> saved = new ArrayList<>();
        ChatSession session = ChatSession.restore(SESSION_ID, UUID.randomUUID(), null, Instant.now(), Instant.now());

        when(chatPersistencePort.findSessionById(SESSION_ID)).thenReturn(Optional.of(session));
        when(chatPersistencePort.saveMessage(any(ChatMessage.class))).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(chatPersistencePort.findMessageById(userMessageId)).thenReturn(Optional.of(userMessage));
        when(chatPersistencePort.findMessageProcessingByUserMessageIdForUpdate(userMessageId))
                .thenReturn(Optional.of(processing(userMessageId)));
        when(chatPersistencePort.saveMessageProcessing(any(ChatMessageProcessing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(chatPersistencePort.saveSession(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return saved;
    }

    private ChatAssistantMetadata emptyMetadata() {
        return new ChatAssistantMetadata(null, null, List.of(), List.of(), false, null, 1);
    }

    private ChatMessageProcessing processing(UUID userMessageId) {
        Instant now = Instant.now();
        return ChatMessageProcessing.restore(
                UUID.randomUUID(),
                userMessageId,
                ChatMessageProcessingStatus.PROCESSING,
                null,
                null,
                now,
                null,
                now,
                now
        );
    }
}
