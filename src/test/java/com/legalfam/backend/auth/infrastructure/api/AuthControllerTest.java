package com.legalfam.backend.auth.infrastructure.api;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.auth.application.dto.TokenResponse;
import com.legalfam.backend.auth.application.service.AuthService;
import com.legalfam.backend.auth.domain.exception.EmailAlreadyExistsException;
import com.legalfam.backend.auth.domain.exception.InvalidCredentialsException;
import com.legalfam.backend.auth.domain.exception.InvalidRefreshTokenException;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
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
                .andExpect(jsonPath("$.code", is("invalid_request")))
                .andExpect(jsonPath("$.message", is("Email, password, name and phone are required")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/signup")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verifyNoInteractions(authService);
    }

    @Test
    void signupReturnsBadRequestWhenEmailIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid-email\",\"password\":\"secret\",\"name\":\"Name\",\"phone\":\"900000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("invalid_request")))
                .andExpect(jsonPath("$.message", is("Valid email is required")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/signup")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verifyNoInteractions(authService);
    }

    @Test
    void signupReturnsCreatedWhenPayloadIsValid() throws Exception {
        when(authService.signup(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new TokenResponse("access-1", "refresh-1", "Bearer", 900));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"secret\",\"name\":\"Juan\",\"phone\":\"900000000\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", is("access-1")))
                .andExpect(jsonPath("$.refreshToken", is("refresh-1")))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", is(900)));
    }

    @Test
    void signupReturnsConflictWhenEmailAlreadyExists() throws Exception {
        when(authService.signup(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new EmailAlreadyExistsException("user@example.com"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"secret\",\"name\":\"Juan\",\"phone\":\"900000000\"}"))
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
        when(authService.login(anyString(), anyString())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"wrong\"}"))
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
                        .content("{\"email\":\"invalid-email\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("invalid_request")))
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
                .andExpect(jsonPath("$.code", is("invalid_request")))
                .andExpect(jsonPath("$.message", is("Refresh token is required")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/refresh")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void refreshReturnsUnauthorizedForInvalidRefreshToken() throws Exception {
        when(authService.refresh(anyString())).thenThrow(new InvalidRefreshTokenException());

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
}
