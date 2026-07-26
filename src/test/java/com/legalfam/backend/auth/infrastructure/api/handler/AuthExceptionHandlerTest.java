package com.legalfam.backend.auth.infrastructure.api.handler;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.auth.domain.exception.EmailAlreadyExistsException;
import com.legalfam.backend.auth.domain.exception.EmailNotVerifiedException;
import com.legalfam.backend.auth.domain.exception.InvalidAuthRequestException;
import com.legalfam.backend.auth.domain.exception.InvalidCredentialsException;
import com.legalfam.backend.auth.infrastructure.api.handler.AuthExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class AuthExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingAuthController())
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    @Test
    void mapsEmailAlreadyExistsToStandardError() throws Exception {
        mockMvc.perform(get("/auth-errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is("conflict_error")))
                .andExpect(jsonPath("$.code", is("email_already_exists")))
                .andExpect(jsonPath("$.message", is("Email already exists")))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.path", is("/auth-errors/conflict")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void mapsInvalidCredentialsToStandardError() throws Exception {
        mockMvc.perform(get("/auth-errors/invalid-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type", is("authentication_error")))
                .andExpect(jsonPath("$.code", is("invalid_credentials")))
                .andExpect(jsonPath("$.message", is("Invalid credentials")))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.path", is("/auth-errors/invalid-credentials")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void mapsEmailNotVerifiedToForbidden() throws Exception {
        mockMvc.perform(get("/auth-errors/email-not-verified"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", is("authorization_error")))
                .andExpect(jsonPath("$.code", is("email_not_verified")))
                .andExpect(jsonPath("$.message", is("Email is not verified")))
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.path", is("/auth-errors/email-not-verified")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void mapsInvalidVerificationTokenToBadRequest() throws Exception {
        mockMvc.perform(get("/auth-errors/verification-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("verification_token_invalid")))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    void mapsInvalidResetTokenToBadRequest() throws Exception {
        mockMvc.perform(get("/auth-errors/reset-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("reset_token_invalid")))
                .andExpect(jsonPath("$.status", is(400)));
    }

    @RestController
    private static class ThrowingAuthController {
        @GetMapping("/auth-errors/conflict")
        String conflict() {
            throw EmailAlreadyExistsException.forEmail("user@example.com");
        }

        @GetMapping("/auth-errors/invalid-credentials")
        String invalidCredentials() {
            throw InvalidCredentialsException.invalidCredentials();
        }

        @GetMapping("/auth-errors/email-not-verified")
        String emailNotVerified() {
            throw EmailNotVerifiedException.forLogin();
        }

        @GetMapping("/auth-errors/verification-token")
        String verificationToken() {
            throw InvalidAuthRequestException.verificationTokenInvalid();
        }

        @GetMapping("/auth-errors/reset-token")
        String resetToken() {
            throw InvalidAuthRequestException.resetTokenInvalid();
        }
    }
}
