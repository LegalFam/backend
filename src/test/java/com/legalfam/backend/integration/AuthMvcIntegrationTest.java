package com.legalfam.backend.integration;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.auth.application.dto.TokenResponse;
import com.legalfam.backend.auth.application.dto.UserResponse;
import com.legalfam.backend.auth.application.port.in.IAuthMailDispatchUseCase;
import com.legalfam.backend.auth.application.port.in.IAuthUseCase;
import com.legalfam.backend.auth.application.port.out.ITokenValidationPort;
import com.legalfam.backend.auth.infrastructure.api.AuthController;
import com.legalfam.backend.auth.infrastructure.api.handler.AuthExceptionHandler;
import com.legalfam.backend.auth.infrastructure.config.CorsProperties;
import com.legalfam.backend.auth.infrastructure.security.JwtAuthenticationFilter;
import com.legalfam.backend.auth.infrastructure.security.SecurityConfig;
import com.legalfam.backend.common.error.handler.GlobalExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AuthController.class)
@Import({
        JwtAuthenticationFilter.class,
        SecurityConfig.class,
        AuthExceptionHandler.class,
        GlobalExceptionHandler.class
})
@EnableConfigurationProperties(CorsProperties.class)
class AuthMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAuthUseCase authUseCase;

    @MockitoBean
    private IAuthMailDispatchUseCase authMailDispatchUseCase;

    @MockitoBean
    private ITokenValidationPort tokenValidationPort;

    @Test
    void signupIsPublicAndUsesValidationAndControllerAdvice() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authUseCase.signup("user@example.com", "secret123", "Juan", "900000000"))
                .thenReturn(new UserResponse(userId, "user@example.com", "Juan", "900000000", false));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "secret123",
                                  "name": " Juan ",
                                  "phone": " 900000000 "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(userId.toString())))
                .andExpect(jsonPath("$.email", is("user@example.com")))
                .andExpect(jsonPath("$.emailVerified", is(false)))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        verify(authUseCase).signup("user@example.com", "secret123", "Juan", "900000000");
    }

    @Test
    void verificationAndPasswordResetEndpointsArePublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\" verify-1 \"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\" reset-1 \",\"newPassword\":\"nueva1234\"}"))
                .andExpect(status().isNoContent());

        verify(authUseCase).confirmEmailVerification("verify-1");
        verify(authUseCase).resetPassword("reset-1", "nueva1234");
        verify(authMailDispatchUseCase).resendEmailVerification("user@example.com");
        verify(authMailDispatchUseCase).requestPasswordReset("user@example.com");
    }

    @Test
    void loginAndRefreshArePublic() throws Exception {
        when(authUseCase.login("user@example.com", "secret123"))
                .thenReturn(new TokenResponse("login-access", "login-refresh", "Bearer", 900));
        when(authUseCase.refresh("refresh-1"))
                .thenReturn(new TokenResponse("new-access", "new-refresh", "Bearer", 900));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("login-access")))
                .andExpect(jsonPath("$.refreshToken", is("login-refresh")));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\" refresh-1 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("new-access")))
                .andExpect(jsonPath("$.refreshToken", is("new-refresh")));

        verify(authUseCase).login("user@example.com", "secret123");
        verify(authUseCase).refresh("refresh-1");
    }

    @Test
    void malformedJsonReturnsGlobalApiError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("malformed_json")))
                .andExpect(jsonPath("$.message", is("Malformed request body")))
                .andExpect(jsonPath("$.path", is("/api/v1/auth/login")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verifyNoInteractions(authUseCase);
    }
}
