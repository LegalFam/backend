package com.legalfam.backend.auth.infrastructure.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.auth.application.dto.UserResponse;
import com.legalfam.backend.auth.application.service.AuthService;
import com.legalfam.backend.auth.domain.exception.InvalidAuthRequestException;
import com.legalfam.backend.auth.infrastructure.api.handler.AuthExceptionHandler;
import com.legalfam.backend.common.error.handler.GlobalExceptionHandler;
import com.legalfam.backend.common.security.AuthenticatedUserResolver;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserProfileController(
                        authService,
                        new AuthenticatedUserResolver()
                ))
                .setControllerAdvice(new AuthExceptionHandler(), new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getProfileReturnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId.toString());

        when(authService.getProfile(eq(userId)))
                .thenReturn(new UserResponse(userId, "maria@ejemplo.com", "Maria Garcia", "987654321"));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(userId.toString())))
                .andExpect(jsonPath("$.email", is("maria@ejemplo.com")))
                .andExpect(jsonPath("$.name", is("Maria Garcia")));
    }

    @Test
    void getProfileReturnsForbiddenWhenPrincipalUserIdIsInvalid() throws Exception {
        authenticateAs("not-a-uuid");

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("forbidden")))
                .andExpect(jsonPath("$.path", is("/api/v1/users/me")));

        verifyNoInteractions(authService);
    }

    @Test
    void updateProfileTrimsNameAndReturnsUpdatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId.toString());

        when(authService.updateProfile(eq(userId), eq("Maria Garcia")))
                .thenReturn(new UserResponse(userId, "maria@ejemplo.com", "Maria Garcia", "987654321"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Maria Garcia  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Maria Garcia")));

        verify(authService).updateProfile(userId, "Maria Garcia");
    }

    @Test
    void updateProfileReturnsBadRequestWhenNameIsBlank() throws Exception {
        authenticateAs(UUID.randomUUID().toString());

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("name_required")));

        verifyNoInteractions(authService);
    }

    @Test
    void updatePasswordReturnsNoContent() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId.toString());

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"actual123\",\"newPassword\":\"nueva1234\"}"))
                .andExpect(status().isNoContent());

        verify(authService).updatePassword(userId, "actual123", "nueva1234");
    }

    @Test
    void updatePasswordReturnsBadRequestWhenCurrentPasswordIsWrong() throws Exception {
        UUID userId = UUID.randomUUID();
        authenticateAs(userId.toString());

        doThrow(InvalidAuthRequestException.currentPasswordInvalid())
                .when(authService).updatePassword(eq(userId), eq("incorrecta"), eq("nueva1234"));

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"incorrecta\",\"newPassword\":\"nueva1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("validation_error")))
                .andExpect(jsonPath("$.code", is("current_password_invalid")));
    }

    @Test
    void updatePasswordReturnsBadRequestWhenNewPasswordIsTooShort() throws Exception {
        authenticateAs(UUID.randomUUID().toString());

        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"actual123\",\"newPassword\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("password_length_invalid")));

        verifyNoInteractions(authService);
    }

    private void authenticateAs(String principalUserId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principalUserId, null, java.util.List.of())
        );
    }
}
