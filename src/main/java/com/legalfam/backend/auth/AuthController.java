package com.legalfam.backend.auth;

import com.legalfam.backend.auth.dto.LoginRequest;
import com.legalfam.backend.auth.dto.RefreshTokenRequest;
import com.legalfam.backend.auth.dto.SignupRequest;
import com.legalfam.backend.auth.dto.TokenResponse;
import com.legalfam.backend.error.ApiError;
import com.legalfam.backend.error.exception.InvalidRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.regex.Pattern;
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

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
    public ResponseEntity<TokenResponse> signup(@RequestBody(required = false) SignupRequest request) {
        if (request == null || isBlank(request.email()) || isBlank(request.password())
                || isBlank(request.name()) || isBlank(request.phone())) {
            throw new InvalidRequestException("Email, password, name and phone are required");
        }
        if (!isValidEmail(request.email().trim())) {
            throw new InvalidRequestException("Valid email is required");
        }

        TokenResponse tokens = authService.signup(
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
    public ResponseEntity<TokenResponse> login(@RequestBody(required = false) LoginRequest request) {
        if (request == null || isBlank(request.email()) || isBlank(request.password())) {
            throw new InvalidRequestException("Email and password are required");
        }
        if (!isValidEmail(request.email().trim())) {
            throw new InvalidRequestException("Valid email is required");
        }

        TokenResponse tokens = authService.login(request.email().trim(), request.password());
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
    public ResponseEntity<TokenResponse> refresh(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request == null || isBlank(request.refreshToken())) {
            throw new InvalidRequestException("Refresh token is required");
        }

        TokenResponse tokens = authService.refresh(request.refreshToken().trim());
        return ResponseEntity.ok(tokens);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isValidEmail(String value) {
        return EMAIL_PATTERN.matcher(value).matches();
    }
}
