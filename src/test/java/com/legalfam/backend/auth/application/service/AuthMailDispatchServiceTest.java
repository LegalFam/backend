package com.legalfam.backend.auth.application.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.legalfam.backend.auth.application.dto.AuthMailDispatch;
import com.legalfam.backend.auth.application.port.in.IAuthUseCase;
import com.legalfam.backend.auth.application.port.out.IAuthMailPort;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthMailDispatchServiceTest {

    @Mock
    private IAuthUseCase authUseCase;
    @Mock
    private IAuthMailPort authMailPort;

    private AuthMailDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        dispatchService = new AuthMailDispatchService(
                authUseCase,
                authMailPort,
                new AuthLinkProperties(
                        "https://legalfam.pe/",
                        "/verificar-correo",
                        "/restablecer-contrasena"
                )
        );
    }

    @Test
    void dispatchEmailVerificationBuildsTheFrontendLink() {
        UUID userId = UUID.randomUUID();
        when(authUseCase.issueEmailVerificationToken(userId))
                .thenReturn(Optional.of(new AuthMailDispatch("user@example.com", "Juan", "raw-token", 1440)));

        dispatchService.dispatchEmailVerification(userId);

        // Trailing slash on the base URL must not produce a double slash.
        verify(authMailPort).sendEmailVerification(
                "user@example.com",
                "Juan",
                "https://legalfam.pe/verificar-correo?token=raw-token",
                1440
        );
    }

    @Test
    void requestPasswordResetBuildsTheResetLink() {
        when(authUseCase.issuePasswordResetToken("user@example.com"))
                .thenReturn(Optional.of(new AuthMailDispatch("user@example.com", "Juan", "reset-token", 60)));

        dispatchService.requestPasswordReset("user@example.com");

        verify(authMailPort).sendPasswordReset(
                "user@example.com",
                "Juan",
                "https://legalfam.pe/restablecer-contrasena?token=reset-token",
                60
        );
    }

    @Test
    void urlEncodesTheToken() {
        when(authUseCase.issuePasswordResetToken("user@example.com"))
                .thenReturn(Optional.of(new AuthMailDispatch("user@example.com", "Juan", "a+b/c=", 60)));

        dispatchService.requestPasswordReset("user@example.com");

        verify(authMailPort).sendPasswordReset(
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.eq(
                        "https://legalfam.pe/restablecer-contrasena?token=a%2Bb%2Fc%3D"),
                anyLong()
        );
    }

    @Test
    void sendsNothingWhenNoTokenWasIssued() {
        UUID userId = UUID.randomUUID();
        when(authUseCase.issueEmailVerificationToken(userId)).thenReturn(Optional.empty());
        when(authUseCase.issuePasswordResetToken("missing@example.com")).thenReturn(Optional.empty());

        dispatchService.dispatchEmailVerification(userId);
        dispatchService.requestPasswordReset("missing@example.com");

        verifyNoInteractions(authMailPort);
    }
}
