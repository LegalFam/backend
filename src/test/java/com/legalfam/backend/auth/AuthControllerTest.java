package com.legalfam.backend.auth;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.auth.dto.TokenResponse;
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
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService)).build();
    }

    @Test
    void signupReturnsBadRequestWhenPayloadIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"   \",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Email and password are required")));

        verifyNoInteractions(authService);
    }

    @Test
    void signupReturnsCreatedWhenPayloadIsValid() throws Exception {
        when(authService.signup(anyString(), anyString()))
                .thenReturn(new TokenResponse("access-1", "refresh-1", "Bearer", 900));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken", is("access-1")))
                .andExpect(jsonPath("$.refreshToken", is("refresh-1")))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresIn", is(900)));
    }

    @Test
    void signupReturnsConflictWhenEmailAlreadyExists() throws Exception {
        when(authService.signup(anyString(), anyString()))
                .thenThrow(new EmailAlreadyExistsException("user@example.com"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Email already exists")));
    }

    @Test
    void loginReturnsUnauthorizedForInvalidCredentials() throws Exception {
        when(authService.login(anyString(), anyString())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid credentials")));
    }

    @Test
    void refreshReturnsBadRequestWhenTokenIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Refresh token is required")));
    }

    @Test
    void refreshReturnsUnauthorizedForInvalidRefreshToken() throws Exception {
        when(authService.refresh(anyString())).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid refresh token")));
    }
}
