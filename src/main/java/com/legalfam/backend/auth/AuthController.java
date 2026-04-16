package com.legalfam.backend.auth;

import com.legalfam.backend.auth.dto.AuthResponse;
import com.legalfam.backend.auth.dto.LoginRequest;
import com.legalfam.backend.auth.dto.RefreshTokenRequest;
import com.legalfam.backend.auth.dto.SignupRequest;
import com.legalfam.backend.auth.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Signup with email and password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already exists",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    })
    @PostMapping("/signup")
    public ResponseEntity<Object> signup(@RequestBody SignupRequest request) {
        if (request == null || isBlank(request.email()) || isBlank(request.password())) {
            return ResponseEntity.badRequest().body(new AuthResponse("Email and password are required"));
        }

        try {
            TokenResponse tokens = authService.signup(request.email().trim(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED).body(tokens);
        } catch (EmailAlreadyExistsException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new AuthResponse("Email already exists"));
        }
    }

    @Operation(summary = "Login with email and password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody LoginRequest request) {
        if (request == null || isBlank(request.email()) || isBlank(request.password())) {
            return ResponseEntity.badRequest().body(new AuthResponse("Email and password are required"));
        }

        try {
            TokenResponse tokens = authService.login(request.email().trim(), request.password());
            return ResponseEntity.ok(tokens);
        } catch (InvalidCredentialsException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse("Invalid credentials"));
        }
    }

    @Operation(summary = "Refresh access token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid refresh token",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<Object> refresh(@RequestBody RefreshTokenRequest request) {
        if (request == null || isBlank(request.refreshToken())) {
            return ResponseEntity.badRequest().body(new AuthResponse("Refresh token is required"));
        }

        try {
            TokenResponse tokens = authService.refresh(request.refreshToken().trim());
            return ResponseEntity.ok(tokens);
        } catch (InvalidRefreshTokenException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new AuthResponse("Invalid refresh token"));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
