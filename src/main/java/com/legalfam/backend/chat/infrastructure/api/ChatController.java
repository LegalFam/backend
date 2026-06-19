package com.legalfam.backend.chat.infrastructure.api;

import com.legalfam.backend.chat.application.port.in.IChatUseCase;
import com.legalfam.backend.chat.application.dto.ChatAskRequest;
import com.legalfam.backend.chat.application.dto.ChatMessageResponse;
import com.legalfam.backend.chat.application.dto.ChatProcessingStatusResponse;
import com.legalfam.backend.chat.application.dto.ChatRateMessageRequest;
import com.legalfam.backend.chat.application.dto.ChatSendAcceptedResponse;
import com.legalfam.backend.chat.application.dto.ChatSessionResponse;
import com.legalfam.backend.chat.application.dto.ChatUpdateSessionRequest;
import com.legalfam.backend.chat.domain.exception.ChatApiError;
import com.legalfam.backend.chat.domain.exception.InvalidChatRequestException;
import com.legalfam.backend.chat.infrastructure.adapter.out.SseChatAssistantDeliveryAdapter;
import com.legalfam.backend.common.error.ApiError;
import com.legalfam.backend.common.openapi.ProtectedApiOperation;
import com.legalfam.backend.common.cursor.CursorQuery;
import com.legalfam.backend.common.cursor.CursorResponse;
import com.legalfam.backend.common.security.AuthenticatedUserResolver;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Chats")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final IChatUseCase IChatUseCase;
    private final SseChatAssistantDeliveryAdapter sseChatAssistantDeliveryAdapter;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public ChatController(
            IChatUseCase IChatUseCase,
            SseChatAssistantDeliveryAdapter sseChatAssistantDeliveryAdapter,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.IChatUseCase = IChatUseCase;
        this.sseChatAssistantDeliveryAdapter = sseChatAssistantDeliveryAdapter;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @GetMapping(value = "/chat/subscribe/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ProtectedApiOperation(summary = "Subscribe to chat updates using SSE")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SSE subscription established"),
            @ApiResponse(responseCode = "404", description = "Session not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<SseEmitter> subscribe(
            @AuthenticationPrincipal String principalUserId,
            @PathVariable("sessionId") UUID sessionId
    ) {
        UUID userId = authenticatedUserResolver.requireUserId(principalUserId);
        IChatUseCase.assertSessionOwnershipExists(userId, sessionId);
        SseEmitter emitter = sseChatAssistantDeliveryAdapter.subscribe(userId, sessionId);
        return ResponseEntity.ok(emitter);
    }

    @PostMapping("/chat/sessions")
    @ProtectedApiOperation(summary = "Create a new chat session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Session created",
                    content = @Content(schema = @Schema(implementation = ChatSessionResponse.class)))
    })
    public ResponseEntity<ChatSessionResponse> createSession(@AuthenticationPrincipal String principalUserId) {
        UUID userId = authenticatedUserResolver.requireUserId(principalUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(IChatUseCase.createSession(userId));
    }

    @PatchMapping("/chat/sessions/{sessionId}")
    @ProtectedApiOperation(summary = "Update a chat session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Session updated",
                    content = @Content(schema = @Schema(implementation = ChatSessionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Session not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<ChatSessionResponse> updateSession(
            @AuthenticationPrincipal String principalUserId,
            @PathVariable("sessionId") UUID sessionId,
            @Valid @RequestBody(required = false) ChatUpdateSessionRequest request
    ) {
        UUID userId = authenticatedUserResolver.requireUserId(principalUserId);
        return ResponseEntity.ok(IChatUseCase.updateSession(userId, sessionId, request));
    }

    @DeleteMapping("/chat/sessions/{sessionId}")
    @ProtectedApiOperation(summary = "Delete a chat session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Session deleted"),
            @ApiResponse(responseCode = "404", description = "Session not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> deleteSession(
            @AuthenticationPrincipal String principalUserId,
            @PathVariable("sessionId") UUID sessionId
    ) {
        UUID userId = authenticatedUserResolver.requireUserId(principalUserId);
        IChatUseCase.deleteSession(userId, sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/chat/send")
    @ProtectedApiOperation(summary = "Send message for asynchronous chat processing")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Message accepted for processing",
                    content = @Content(schema = @Schema(implementation = ChatSendAcceptedResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Assistant receipt confirmation is still pending",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<ChatSendAcceptedResponse> send(
            @AuthenticationPrincipal String principalUserId,
            @Valid @RequestBody(required = false) ChatAskRequest request
    ) {
        if (request == null) {
            log.warn("Chat request rejected: blank message");
            throw InvalidChatRequestException.of(ChatApiError.MESSAGE_REQUIRED);
        }

        UUID userId = authenticatedUserResolver.requireUserId(principalUserId);
        log.info("Chat request accepted: messageLength={}", request.message().trim().length());
        ChatSendAcceptedResponse response = IChatUseCase.send(
                userId,
                request.message().trim(),
                request.sessionId()
        );
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/chat/processing-status")
    @ProtectedApiOperation(summary = "Get current chat processing status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Processing status fetched",
                    content = @Content(schema = @Schema(implementation = ChatProcessingStatusResponse.class)))
    })
    public ResponseEntity<ChatProcessingStatusResponse> getProcessingStatus(
            @AuthenticationPrincipal String principalUserId
    ) {
        UUID userId = authenticatedUserResolver.requireUserId(principalUserId);
        return ResponseEntity.ok(IChatUseCase.getProcessingStatus(userId));
    }

    @GetMapping("/chat/sessions")
    @ProtectedApiOperation(summary = "List chat sessions for authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sessions fetched",
                    content = @Content(schema = @Schema(implementation = CursorResponse.class)))
    })
    public ResponseEntity<CursorResponse<ChatSessionResponse>> listSessions(
            @AuthenticationPrincipal String principalUserId,
            @RequestParam(required = false) Integer cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = authenticatedUserResolver.requireUserId(principalUserId);
        return ResponseEntity.ok(CursorResponse.from(IChatUseCase.listSessions(userId, cursorQuery(cursor, size))));
    }

    @GetMapping("/chat/sessions/{sessionId}/messages")
    @ProtectedApiOperation(summary = "List messages for a chat session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Messages fetched",
                    content = @Content(schema = @Schema(implementation = CursorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Session not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<CursorResponse<ChatMessageResponse>> listMessages(
            @AuthenticationPrincipal String principalUserId,
            @PathVariable("sessionId") @Parameter(description = "Chat session id") UUID sessionId,
            @RequestParam(required = false) Integer cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = authenticatedUserResolver.requireUserId(principalUserId);
        return ResponseEntity.ok(CursorResponse.from(
                IChatUseCase.listMessages(userId, sessionId, cursorQuery(cursor, size))
        ));
    }

    @PatchMapping("/chat/messages/{messageId}/rating")
    @ProtectedApiOperation(summary = "Rate a chat message")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rating updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Message not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> rateMessage(
            @AuthenticationPrincipal String principalUserId,
            @PathVariable("messageId") UUID messageId,
            @Valid @RequestBody(required = false) ChatRateMessageRequest request
    ) {
        UUID userId = authenticatedUserResolver.requireUserId(principalUserId);
        IChatUseCase.rateMessage(userId, messageId, request);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/chat/messages/{messageId}/receipt")
    @ProtectedApiOperation(summary = "Confirm assistant message receipt")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Receipt confirmed"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Message not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> confirmReceipt(
            @AuthenticationPrincipal String principalUserId,
            @PathVariable("messageId") UUID messageId
    ) {
        UUID userId = authenticatedUserResolver.requireUserId(principalUserId);
        IChatUseCase.confirmAssistantReceipt(userId, messageId);
        return ResponseEntity.noContent().build();
    }

    private CursorQuery cursorQuery(Integer cursor, int size) {
        try {
            return CursorQuery.of(cursor, size);
        } catch (IllegalArgumentException ex) {
            throw InvalidChatRequestException.of(ChatApiError.CURSOR_INVALID);
        }
    }

}
