package com.legalfam.backend.auth.infrastructure.api;

import com.legalfam.backend.auth.application.port.in.IAuthMailDispatchUseCase;
import com.legalfam.backend.auth.application.port.in.IAuthUseCase;
import com.legalfam.backend.auth.application.dto.ForgotPasswordRequest;
import com.legalfam.backend.auth.application.dto.LoginRequest;
import com.legalfam.backend.auth.application.dto.RefreshTokenRequest;
import com.legalfam.backend.auth.application.dto.ResendVerificationRequest;
import com.legalfam.backend.auth.application.dto.ResetPasswordRequest;
import com.legalfam.backend.auth.application.dto.SignupRequest;
import com.legalfam.backend.auth.application.dto.TokenResponse;
import com.legalfam.backend.auth.application.dto.UserResponse;
import com.legalfam.backend.auth.application.dto.VerifyEmailRequest;
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
    private final IAuthMailDispatchUseCase IAuthMailDispatchUseCase;

    public AuthController(IAuthUseCase IAuthUseCase, IAuthMailDispatchUseCase IAuthMailDispatchUseCase) {
        this.IAuthUseCase = IAuthUseCase;
        this.IAuthMailDispatchUseCase = IAuthMailDispatchUseCase;
    }

    @Operation(summary = "Signup with email, password, name and phone. Sends a verification email; no tokens are issued until the email is verified")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created, pending email verification",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Email already exists",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody(required = false) SignupRequest request) {
        if (request == null) {
            throw InvalidAuthRequestException.signupRequestRequired();
        }

        UserResponse user = IAuthUseCase.signup(
                request.email().trim(),
                request.password(),
                request.name().trim(),
                request.phone().trim()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @Operation(summary = "Login with email and password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Email is not verified",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody(required = false) LoginRequest request) {
        if (request == null) {
            throw InvalidAuthRequestException.loginRequestRequired();
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
            throw InvalidAuthRequestException.refreshTokenRequired();
        }

        TokenResponse tokens = IAuthUseCase.refresh(request.refreshToken().trim());
        return ResponseEntity.ok(tokens);
    }

    @Operation(summary = "Confirm an email address with the token from the verification email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Email verified"),
            @ApiResponse(responseCode = "400", description = "Token is missing, invalid or expired",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody(required = false) VerifyEmailRequest request) {
        if (request == null) {
            throw InvalidAuthRequestException.verifyEmailRequestRequired();
        }

        IAuthUseCase.confirmEmailVerification(request.token().trim());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Resend the verification email. Always returns 204 so registered addresses cannot be enumerated")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Request accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(
            @Valid @RequestBody(required = false) ResendVerificationRequest request
    ) {
        if (request == null) {
            throw InvalidAuthRequestException.resendVerificationRequestRequired();
        }

        IAuthMailDispatchUseCase.resendEmailVerification(request.email().trim());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Request a password reset email. Always returns 204 so registered addresses cannot be enumerated")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Request accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody(required = false) ForgotPasswordRequest request) {
        if (request == null) {
            throw InvalidAuthRequestException.forgotPasswordRequestRequired();
        }

        IAuthMailDispatchUseCase.requestPasswordReset(request.email().trim());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set a new password with the token from the reset email. Revokes every active session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Password updated"),
            @ApiResponse(responseCode = "400", description = "Token is missing, invalid or expired, or the password is too short",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody(required = false) ResetPasswordRequest request) {
        if (request == null) {
            throw InvalidAuthRequestException.resetPasswordRequestRequired();
        }

        IAuthUseCase.resetPassword(request.token().trim(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
