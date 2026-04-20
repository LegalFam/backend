package com.legalfam.backend.chat;

import com.legalfam.backend.chat.dto.ChatAskRequest;
import com.legalfam.backend.chat.dto.ChatAskResponse;
import com.legalfam.backend.chat.dto.ChatMessageResponse;
import com.legalfam.backend.chat.dto.ChatRateMessageRequest;
import com.legalfam.backend.chat.dto.ChatSessionResponse;
import com.legalfam.backend.error.ApiError;
import com.legalfam.backend.error.exception.InvalidRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Chats")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    @Operation(summary = "Chat using n8n workflow")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Chat response generated",
                    content = @Content(schema = @Schema(implementation = ChatAskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "502", description = "Upstream service unavailable",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<ChatAskResponse> chat(
            @AuthenticationPrincipal String principalUserId,
            @RequestBody(required = false) ChatAskRequest request
    ) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            log.warn("Chat request rejected: blank message");
            throw new InvalidRequestException("Message is required");
        }

        UUID userId = parsePrincipalUserId(principalUserId);
        log.info("Chat request started: messageLength={}", request.message().trim().length());
        ChatAskResponse response = chatService.chat(
                userId,
                request.message().trim(),
                request.sessionId()
        );
        log.info("Chat request completed: citations={}", response.citations() == null ? 0 : response.citations().size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/chat/sessions")
    @Operation(summary = "List chat sessions for authenticated user")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sessions fetched",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChatSessionResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<List<ChatSessionResponse>> listSessions(@AuthenticationPrincipal String principalUserId) {
        UUID userId = parsePrincipalUserId(principalUserId);
        return ResponseEntity.ok(chatService.listSessions(userId));
    }

    @GetMapping("/chat/sessions/{sessionId}/messages")
    @Operation(summary = "List messages for a chat session")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Messages fetched",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ChatMessageResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Session not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<List<ChatMessageResponse>> listMessages(
            @AuthenticationPrincipal String principalUserId,
            @PathVariable("sessionId") @Parameter(description = "Chat session id") UUID sessionId
    ) {
        UUID userId = parsePrincipalUserId(principalUserId);
        return ResponseEntity.ok(chatService.listMessages(userId, sessionId));
    }

    @PatchMapping("/chat/messages/{messageId}/rating")
    @Operation(summary = "Rate a chat message")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rating updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Message not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> rateMessage(
            @AuthenticationPrincipal String principalUserId,
            @PathVariable("messageId") UUID messageId,
            @RequestBody(required = false) ChatRateMessageRequest request
    ) {
        UUID userId = parsePrincipalUserId(principalUserId);
        chatService.rateMessage(userId, messageId, request);
        return ResponseEntity.ok().build();
    }

    private UUID parsePrincipalUserId(String principalUserId) {
        if (principalUserId == null || principalUserId.isBlank()) {
            throw new InvalidRequestException("Authenticated user is required");
        }
        try {
            return UUID.fromString(principalUserId.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Authenticated user id is invalid");
        }
    }
}
