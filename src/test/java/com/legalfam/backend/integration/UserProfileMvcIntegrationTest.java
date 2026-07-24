package com.legalfam.backend.integration;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.legalfam.backend.auth.application.port.in.IAuthUseCase;
import com.legalfam.backend.auth.application.port.out.ITokenValidationPort;
import com.legalfam.backend.auth.infrastructure.api.UserProfileController;
import com.legalfam.backend.auth.infrastructure.api.handler.AuthExceptionHandler;
import com.legalfam.backend.auth.infrastructure.config.CorsProperties;
import com.legalfam.backend.auth.infrastructure.security.JwtAuthenticationFilter;
import com.legalfam.backend.auth.infrastructure.security.SecurityConfig;
import com.legalfam.backend.common.error.handler.GlobalExceptionHandler;
import com.legalfam.backend.common.security.AuthenticatedUserResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UserProfileController.class)
@Import({
        AuthenticatedUserResolver.class,
        JwtAuthenticationFilter.class,
        SecurityConfig.class,
        AuthExceptionHandler.class,
        GlobalExceptionHandler.class
})
@EnableConfigurationProperties(CorsProperties.class)
class UserProfileMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAuthUseCase authUseCase;

    @MockitoBean
    private ITokenValidationPort tokenValidationPort;

    @Test
    void getProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type", is("authentication_error")))
                .andExpect(jsonPath("$.code", is("unauthorized")))
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.path", is("/api/v1/users/me")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));

        verifyNoInteractions(authUseCase);
    }

    @Test
    void updateProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Maria Garcia\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authUseCase);
    }

    @Test
    void updatePasswordRequiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"actual123\",\"newPassword\":\"nueva1234\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(authUseCase);
    }
}
