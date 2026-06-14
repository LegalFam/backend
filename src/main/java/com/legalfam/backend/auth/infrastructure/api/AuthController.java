package com.legalfam.backend.auth.infrastructure.api;

import com.legalfam.backend.auth.application.port.in.IAuthUseCase;
import com.legalfam.backend.auth.application.dto.LoginRequest;
import com.legalfam.backend.auth.application.dto.RefreshTokenRequest;
import com.legalfam.backend.auth.application.dto.SignupRequest;
import com.legalfam.backend.auth.application.dto.TokenResponse;
import com.legalfam.backend.auth.domain.exception.InvalidAuthRequestException;
import com.legalfam.backend.common.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final IAuthUseCase IAuthUseCase;

    public AuthController(IAuthUseCase IAuthUseCase) {
        this.IAuthUseCase = IAuthUseCase;
    }

    @Operation(summary = "Signup with email, password, name and phone")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Email already exists",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/signup")
    public ResponseEntity<TokenResponse> signup(@Valid @RequestBody(required = false) SignupRequest request) {
        if (request == null) {
            throw new InvalidAuthRequestException("Email, password, name and phone are required");
        }

        TokenResponse tokens = IAuthUseCase.signup(
                request.email().trim(),
                request.password(),
                request.name().trim(),
                request.phone().trim()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(tokens);
    }

    @Operation(summary = "Login with email and password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody(required = false) LoginRequest request) {
        if (request == null) {
            throw new InvalidAuthRequestException("Email and password are required");
        }

        TokenResponse tokens = IAuthUseCase.login(request.email().trim(), request.password());
        return ResponseEntity.ok(tokens);
    }

    @Operation(summary = "Refresh access token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Invalid refresh token",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody(required = false) RefreshTokenRequest request) {
        if (request == null) {
            throw new InvalidAuthRequestException("Refresh token is required");
        }

        TokenResponse tokens = IAuthUseCase.refresh(request.refreshToken().trim());
        return ResponseEntity.ok(tokens);
    }
}
