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
import com.legalfam.backend.auth.application.port.in.IAuthUseCase;
import com.legalfam.backend.auth.application.port.out.ITokenValidationPort;
import com.legalfam.backend.auth.infrastructure.api.AuthController;
import com.legalfam.backend.auth.infrastructure.api.handler.AuthExceptionHandler;
import com.legalfam.backend.auth.infrastructure.config.CorsProperties;
import com.legalfam.backend.auth.infrastructure.security.JwtAuthenticationFilter;
import com.legalfam.backend.auth.infrastructure.security.SecurityConfig;
import com.legalfam.backend.common.error.handler.GlobalExceptionHandler;
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
    private ITokenValidationPort tokenValidationPort;

    @Test
    void signupIsPublicAndUsesValidationAndControllerAdvice() throws Exception {
        when(authUseCase.signup("user@example.com", "secret123", "Juan", "900000000"))
                .thenReturn(new TokenResponse("access-1", "refresh-1", "Bearer", 900));

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
                .andExpect(jsonPath("$.accessToken", is("access-1")))
                .andExpect(jsonPath("$.refreshToken", is("refresh-1")))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", is(900)));

        verify(authUseCase).signup("user@example.com", "secret123", "Juan", "900000000");
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
