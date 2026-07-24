package com.legalfam.backend.auth.infrastructure.api;

import com.legalfam.backend.auth.application.dto.UpdatePasswordRequest;
import com.legalfam.backend.auth.application.dto.UpdateProfileRequest;
import com.legalfam.backend.auth.application.dto.UserResponse;
import com.legalfam.backend.auth.application.port.in.IAuthUseCase;
import com.legalfam.backend.auth.domain.exception.InvalidAuthRequestException;
import com.legalfam.backend.common.error.ApiError;
import com.legalfam.backend.common.openapi.ProtectedApiOperation;
import com.legalfam.backend.common.security.AuthenticatedUserResolver;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserProfileController {

    private final IAuthUseCase IAuthUseCase;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public UserProfileController(
            IAuthUseCase IAuthUseCase,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.IAuthUseCase = IAuthUseCase;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @GetMapping("/me")
    @ProtectedApiOperation(summary = "Get the authenticated user profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile fetched",
                    content = @Content(schema = @Schema(implementation = UserResponse.class)))
    })
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal String principalUserId) {
        UUID userId = authenticatedUserResolver.requireUserId(principalUserId);
        return ResponseEntity.ok(IAuthUseCase.getProfile(userId));
    }

    @PatchMapping("/me")
    @ProtectedApiOperation(summary = "Update the authenticated user profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile updated",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal String principalUserId,
            @Valid @RequestBody(required = false) UpdateProfileRequest request
    ) {
        if (request == null) {
            throw InvalidAuthRequestException.profileRequestRequired();
        }

        UUID userId = authenticatedUserResolver.requireUserId(principalUserId);
        return ResponseEntity.ok(IAuthUseCase.updateProfile(userId, request.name().trim()));
    }

    @PatchMapping("/me/password")
    @ProtectedApiOperation(summary = "Update the authenticated user password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Password updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> updatePassword(
            @AuthenticationPrincipal String principalUserId,
            @Valid @RequestBody(required = false) UpdatePasswordRequest request
    ) {
        if (request == null) {
            throw InvalidAuthRequestException.passwordRequestRequired();
        }

        UUID userId = authenticatedUserResolver.requireUserId(principalUserId);
        IAuthUseCase.updatePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
