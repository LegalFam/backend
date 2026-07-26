package com.legalfam.backend.auth.infrastructure.api;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.auth.application.dto.UserResponse;
import com.legalfam.backend.auth.application.service.AuthMailDispatchService;
import com.legalfam.backend.auth.application.service.AuthService;
import com.legalfam.backend.auth.domain.exception.EmailAlreadyExistsException;
import com.legalfam.backend.auth.domain.exception.EmailNotVerifiedException;
import com.legalfam.backend.auth.domain.exception.InvalidAuthRequestException;
import com.legalfam.backend.auth.domain.exception.InvalidCredentialsException;
import com.legalfam.backend.auth.domain.exception.InvalidRefreshTokenException;
import java.util.UUID;
import com.legalfam.backend.auth.infrastructure.api.AuthController;
import com.legalfam.backend.auth.infrastructure.api.handler.AuthExceptionHandler;
import com.legalfam.backend.common.error.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private AuthMailDispatchService authMailDispatchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, authMailDispatchService))
                .setControllerAdvice(new AuthExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    void signupReturnsBadRequestWhenPayloadIsInvalid() throws Exception {
                mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"   \",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("email_required")))
                .andExpect(jsonPath("$.message", is("Email is required")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/signup")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verifyNoInteractions(authService);
    }

    @Test
    void signupReturnsBadRequestWhenEmailIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid-email\",\"password\":\"secret123\",\"name\":\"Name\",\"phone\":\"900000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("email_invalid")))
                .andExpect(jsonPath("$.message", is("Valid email is required")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/signup")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verifyNoInteractions(authService);
    }

    @Test
    void signupReturnsCreatedUserWithoutTokens() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.signup(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new UserResponse(userId, "user@example.com", "Juan", "900000000", false));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"secret123\",\"name\":\"Juan\",\"phone\":\"900000000\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(userId.toString())))
                .andExpect(jsonPath("$.email", is("user@example.com")))
                .andExpect(jsonPath("$.emailVerified", is(false)))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void signupReturnsConflictWhenEmailAlreadyExists() throws Exception {
        when(authService.signup(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(EmailAlreadyExistsException.forEmail("user@example.com"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"secret123\",\"name\":\"Juan\",\"phone\":\"900000000\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is("conflict_error")))
                .andExpect(jsonPath("$.code", is("email_already_exists")))
                .andExpect(jsonPath("$.message", is("Email already exists")))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/signup")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void loginReturnsUnauthorizedForInvalidCredentials() throws Exception {
        when(authService.login(anyString(), anyString())).thenThrow(InvalidCredentialsException.invalidCredentials());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"wrong123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type", is("authentication_error")))
                .andExpect(jsonPath("$.code", is("invalid_credentials")))
                .andExpect(jsonPath("$.message", is("Invalid credentials")))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/login")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void loginReturnsBadRequestWhenEmailIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid-email\",\"password\":\"secret123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("email_invalid")))
                .andExpect(jsonPath("$.message", is("Valid email is required")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/login")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verifyNoInteractions(authService);
    }

    @Test
    void refreshReturnsBadRequestWhenTokenIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("refresh_token_required")))
                .andExpect(jsonPath("$.message", is("Refresh token is required")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/refresh")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void refreshReturnsUnauthorizedForInvalidRefreshToken() throws Exception {
        when(authService.refresh(anyString())).thenThrow(InvalidRefreshTokenException.invalidRefreshToken());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type", is("authentication_error")))
                .andExpect(jsonPath("$.code", is("invalid_refresh_token")))
                .andExpect(jsonPath("$.message", is("Invalid refresh token")))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/refresh")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void loginReturnsForbiddenWhenEmailIsNotVerified() throws Exception {
        when(authService.login(anyString(), anyString())).thenThrow(EmailNotVerifiedException.forLogin());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", is("authorization_error")))
                .andExpect(jsonPath("$.code", is("email_not_verified")))
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/login")));
    }

    @Test
    void verifyEmailReturnsNoContentWhenTokenIsValid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"valid-token\"}"))
                .andExpect(status().isNoContent());

        verify(authService).confirmEmailVerification("valid-token");
    }

    @Test
    void verifyEmailReturnsBadRequestWhenTokenIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("token_required")));

        verifyNoInteractions(authService);
    }

    @Test
    void verifyEmailReturnsBadRequestWhenTokenIsExpired() throws Exception {
        doThrow(InvalidAuthRequestException.verificationTokenInvalid())
                .when(authService).confirmEmailVerification(anyString());

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"expired-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("verification_token_invalid")));
    }

    @Test
    void resendVerificationReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isNoContent());

        verify(authMailDispatchService).resendEmailVerification("user@example.com");
    }

    @Test
    void forgotPasswordReturnsNoContentAndLeaksNothingForUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.com\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(authMailDispatchService).requestPasswordReset("missing@example.com");
    }

    @Test
    void forgotPasswordReturnsBadRequestWhenEmailIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("email_invalid")));

        verifyNoInteractions(authMailDispatchService);
    }

    @Test
    void resetPasswordReturnsNoContentWhenPayloadIsValid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"reset-token\",\"newPassword\":\"nueva1234\"}"))
                .andExpect(status().isNoContent());

        verify(authService).resetPassword("reset-token", "nueva1234");
    }

    @Test
    void resetPasswordReturnsBadRequestWhenPasswordIsTooShort() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"reset-token\",\"newPassword\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("password_length_invalid")));

        verifyNoInteractions(authService);
    }

    @Test
    void resetPasswordReturnsBadRequestWhenTokenIsExpired() throws Exception {
        doThrow(InvalidAuthRequestException.resetTokenInvalid())
                .when(authService).resetPassword(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"expired-token\",\"newPassword\":\"nueva1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("reset_token_invalid")));
    }
}
