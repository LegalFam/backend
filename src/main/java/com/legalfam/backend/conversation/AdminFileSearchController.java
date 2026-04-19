package com.legalfam.backend.conversation;

import com.legalfam.backend.auth.AdminAccessService;
import com.legalfam.backend.conversation.dto.FileSearchStoreCreateRequest;
import com.legalfam.backend.conversation.dto.FileSearchStoreResponse;
import com.legalfam.backend.conversation.dto.FileSearchUploadResponse;
import com.legalfam.backend.conversation.gemini.GeminiFileSearchUploadClient;
import com.legalfam.backend.error.ApiError;
import com.legalfam.backend.error.exception.InvalidRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/file-search")
@Tag(name = "File Search Admin")
public class AdminFileSearchController {

    private static final Logger log = LoggerFactory.getLogger(AdminFileSearchController.class);

    private final AdminAccessService adminAccessService;
    private final GeminiFileSearchUploadClient geminiFileSearchUploadClient;

    public AdminFileSearchController(
            AdminAccessService adminAccessService,
            GeminiFileSearchUploadClient geminiFileSearchUploadClient
    ) {
        this.adminAccessService = adminAccessService;
        this.geminiFileSearchUploadClient = geminiFileSearchUploadClient;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document directly to Gemini File Search store (admin only)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Upload accepted",
                    content = @Content(schema = @Schema(implementation = FileSearchUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "502", description = "Gemini service unavailable",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<FileSearchUploadResponse> upload(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileSearchStoreName") String fileSearchStoreName,
            @RequestParam(value = "displayName", required = false) String displayName
    ) {
        log.info(
                "Admin upload request received: user={}, fileName={}, sizeBytes={}, fileSearchStoreName={}, displayName={}",
                authentication != null ? authentication.getName() : "anonymous",
                file != null ? file.getOriginalFilename() : "null",
                file != null ? file.getSize() : -1,
                fileSearchStoreName,
                displayName
        );
        adminAccessService.requireAdmin(authentication);

        if (file == null || file.isEmpty()) {
            log.warn("Admin upload rejected: file missing or empty");
            throw new InvalidRequestException("File is required");
        }

        FileSearchUploadResponse response = geminiFileSearchUploadClient.uploadDocument(file, displayName, fileSearchStoreName);
        log.info(
                "Admin upload completed: operationName={}, done={}, documentName={}",
                response.operationName(),
                response.done(),
                response.documentName()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stores")
    @Operation(summary = "List Gemini File Search stores (admin only)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stores listed",
                    content = @Content(schema = @Schema(implementation = FileSearchStoreResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "502", description = "Gemini service unavailable",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<List<FileSearchStoreResponse>> listStores(Authentication authentication) {
        log.info("Admin list stores request received: user={}", authentication != null ? authentication.getName() : "anonymous");
        adminAccessService.requireAdmin(authentication);

        List<FileSearchStoreResponse> stores = geminiFileSearchUploadClient.listStores();
        log.info("Admin list stores completed: count={}", stores.size());
        return ResponseEntity.ok(stores);
    }

    @PostMapping("/stores")
    @Operation(summary = "Create Gemini File Search store (admin only)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Store created",
                    content = @Content(schema = @Schema(implementation = FileSearchStoreResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "502", description = "Gemini service unavailable",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<FileSearchStoreResponse> createStore(
            Authentication authentication,
            @RequestBody(required = false) FileSearchStoreCreateRequest request
    ) {
        String displayName = request != null ? request.displayName() : null;
        log.info(
                "Admin create store request received: user={}, displayName={}",
                authentication != null ? authentication.getName() : "anonymous",
                displayName
        );
        adminAccessService.requireAdmin(authentication);

        FileSearchStoreResponse created = geminiFileSearchUploadClient.createStore(displayName);
        log.info("Admin create store completed: storeName={}", created.name());
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/stores")
    @Operation(summary = "Delete Gemini File Search store (admin only)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Store deleted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "502", description = "Gemini service unavailable",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Map<String, Object>> deleteStore(
            Authentication authentication,
            @RequestParam("name") String name,
            @RequestParam(value = "force", defaultValue = "false") boolean force
    ) {
        log.info(
                "Admin delete store request received: user={}, name={}, force={}",
                authentication != null ? authentication.getName() : "anonymous",
                name,
                force
        );
        adminAccessService.requireAdmin(authentication);

        geminiFileSearchUploadClient.deleteStore(name, force);
        log.info("Admin delete store completed: name={}, force={}", name, force);
        return ResponseEntity.ok(Map.of());
    }
}
