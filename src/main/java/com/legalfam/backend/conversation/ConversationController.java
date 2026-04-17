package com.legalfam.backend.conversation;

import com.legalfam.backend.conversation.dto.ConversationAskRequest;
import com.legalfam.backend.conversation.dto.ConversationAskResponse;
import com.legalfam.backend.error.ApiError;
import com.legalfam.backend.error.exception.InvalidRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversations")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a conversation question using Gemini file search")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conversation response generated",
                    content = @Content(schema = @Schema(implementation = ConversationAskResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "502", description = "Gemini service unavailable",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<ConversationAskResponse> ask(@RequestBody(required = false) ConversationAskRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            log.warn("Conversation ask rejected: blank prompt");
            throw new InvalidRequestException("Prompt is required");
        }

        log.info("Conversation ask started: promptLength={}", request.prompt().trim().length());
        ConversationAskResponse response = conversationService.askWithFileSearch(request.prompt().trim());
        log.info("Conversation ask completed: citations={}", response.citations() == null ? 0 : response.citations().size());
        return ResponseEntity.ok(response);
    }
}
