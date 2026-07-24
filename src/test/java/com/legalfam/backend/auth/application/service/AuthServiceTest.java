package com.legalfam.backend.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.auth.application.port.out.IAccessTokenPort;
import com.legalfam.backend.auth.application.port.out.IAuthEventPublisherPort;
import com.legalfam.backend.auth.application.port.out.IUserPort;
import com.legalfam.backend.auth.application.port.out.IRefreshTokenPersistencePort;
import com.legalfam.backend.common.identity.event.UserRegisteredEvent;
import com.legalfam.backend.auth.domain.exception.EmailAlreadyExistsException;
import com.legalfam.backend.auth.domain.exception.InvalidAuthRequestException;
import com.legalfam.backend.auth.domain.exception.InvalidCredentialsException;
import com.legalfam.backend.auth.domain.exception.InvalidRefreshTokenException;
import com.legalfam.backend.auth.application.dto.TokenResponse;
import com.legalfam.backend.auth.domain.model.RefreshToken;
import com.legalfam.backend.auth.domain.model.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final long REFRESH_EXPIRATION_MS = 86_400_000L;

    @Mock
    private IUserPort IUserPort;
    @Mock
    private IRefreshTokenPersistencePort IRefreshTokenPersistencePort;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private IAccessTokenPort IAccessTokenPort;
    @Mock
    private IAuthEventPublisherPort IAuthEventPublisherPort;

    @Captor
    private ArgumentCaptor<User> userCaptor;
    @Captor
    private ArgumentCaptor<RefreshToken> refreshTokenCaptor;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                IUserPort,
                IRefreshTokenPersistencePort,
                passwordEncoder,
                IAccessTokenPort,
                IAuthEventPublisherPort,
                new AuthTokenProperties(REFRESH_EXPIRATION_MS)
        );
    }

    @Test
    void signupThrowsWhenEmailAlreadyExists() {
        when(IUserPort.existsByEmail("user@example.com")).thenReturn(true);

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> authService.signup("user@example.com", "secret", "Juan", "900000000")
        );

        verify(IUserPort, never()).save(any(User.class));
    }

    @Test
    void signupCreatesUserAndReturnsTokens() {
        UUID userId = UUID.randomUUID();
        when(IUserPort.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed-password");
        when(IUserPort.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return User.restore(userId, user.getEmail(), user.getPassword(), user.getName(), user.getPhone());
        });
        when(IAccessTokenPort.generateAccessToken(eq(userId), eq("user@example.com"))).thenReturn("access-123");
        when(IAccessTokenPort.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(IRefreshTokenPersistencePort.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TokenResponse response = authService.signup("user@example.com", "secret", "Juan", "900000000");

        verify(IUserPort).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("user@example.com", savedUser.getEmail());
        assertEquals("hashed-password", savedUser.getPassword());
        assertEquals("Juan", savedUser.getName());
        assertEquals("900000000", savedUser.getPhone());
        verify(IAuthEventPublisherPort).publishUserRegistered(new UserRegisteredEvent(userId));

        verify(IRefreshTokenPersistencePort).save(refreshTokenCaptor.capture());
        RefreshToken refreshToken = refreshTokenCaptor.getValue();
        assertNotNull(refreshToken.getToken());
        assertNotEquals(response.refreshToken(), refreshToken.getToken());
        assertEquals(hashRefreshToken(response.refreshToken()), refreshToken.getToken());
        assertFalse(refreshToken.isRevoked());
        assertEquals(userId, refreshToken.getUserId());
        assertTrue(refreshToken.getExpiresAt().isAfter(Instant.now()));

        assertEquals("access-123", response.accessToken());
        assertNotEquals(refreshToken.getToken(), response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900L, response.expiresIn());
    }

    @Test
    void loginThrowsWhenUserDoesNotExist() {
        when(IUserPort.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login("missing@example.com", "secret")
        );
    }

    @Test
    void loginThrowsWhenPasswordDoesNotMatch() {
        User user = User.restore(UUID.randomUUID(), "user@example.com", "stored-hash", "Juan", "900000000");
        when(IUserPort.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login("user@example.com", "wrong-password")
        );
    }

    @Test
    void refreshThrowsWhenTokenIsRevoked() {
        User user = User.restore(UUID.randomUUID(), "user@example.com", "stored-hash", "Juan", "900000000");
        String rawToken = "revoked-token";
        RefreshToken existing = RefreshToken.issue(hashRefreshToken(rawToken), user.getId(), Instant.now().plusSeconds(60));
        existing.revoke();
        when(IRefreshTokenPersistencePort.findByToken(hashRefreshToken(rawToken))).thenReturn(Optional.of(existing));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(rawToken)
        );

        verify(IRefreshTokenPersistencePort, never()).save(any(RefreshToken.class));
    }

    @Test
    void refreshThrowsWhenTokenIsExpired() {
        User user = User.restore(UUID.randomUUID(), "user@example.com", "stored-hash", "Juan", "900000000");
        String rawToken = "expired-token";
        RefreshToken existing = RefreshToken.issue(hashRefreshToken(rawToken), user.getId(), Instant.now().minusSeconds(60));
        when(IRefreshTokenPersistencePort.findByToken(hashRefreshToken(rawToken))).thenReturn(Optional.of(existing));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(rawToken)
        );

        verify(IRefreshTokenPersistencePort, never()).save(any(RefreshToken.class));
    }

    @Test
    void refreshThrowsWhenTokenDoesNotExist() {
        String rawToken = "missing-token";
        when(IRefreshTokenPersistencePort.findByToken(hashRefreshToken(rawToken))).thenReturn(Optional.empty());

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(rawToken)
        );

        verify(IRefreshTokenPersistencePort, never()).save(any(RefreshToken.class));
        verify(IUserPort, never()).findById(any(UUID.class));
    }

    @Test
    void refreshRevokesOldTokenButFailsWhenUserNoLongerExists() {
        UUID userId = UUID.randomUUID();
        String oldRefreshRaw = "orphan-refresh";
        String oldRefreshHashed = hashRefreshToken(oldRefreshRaw);
        RefreshToken existing = RefreshToken.issue(oldRefreshHashed, userId, Instant.now().plusSeconds(60));
        when(IRefreshTokenPersistencePort.findByToken(oldRefreshHashed)).thenReturn(Optional.of(existing));
        when(IRefreshTokenPersistencePort.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(IUserPort.findById(userId)).thenReturn(Optional.empty());

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(oldRefreshRaw)
        );

        verify(IRefreshTokenPersistencePort).save(refreshTokenCaptor.capture());
        assertTrue(refreshTokenCaptor.getValue().isRevoked());
        verify(IAccessTokenPort, never()).generateAccessToken(any(UUID.class), any(String.class));
    }

    @Test
    void refreshRevokesOldTokenAndIssuesNewOne() {
        User user = User.restore(UUID.randomUUID(), "user@example.com", "stored-hash", "Juan", "900000000");
        String oldRefreshRaw = "old-refresh";
        String oldRefreshHashed = hashRefreshToken(oldRefreshRaw);
        RefreshToken existing = RefreshToken.issue(oldRefreshHashed, user.getId(), Instant.now().plusSeconds(60));
        when(IRefreshTokenPersistencePort.findByToken(oldRefreshHashed)).thenReturn(Optional.of(existing));
        when(IUserPort.findById(user.getId())).thenReturn(Optional.of(user));
        when(IRefreshTokenPersistencePort.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(IAccessTokenPort.generateAccessToken(eq(user.getId()), eq("user@example.com"))).thenReturn("new-access");
        when(IAccessTokenPort.getAccessTokenExpirationSeconds()).thenReturn(900L);

        TokenResponse response = authService.refresh(oldRefreshRaw);

        verify(IRefreshTokenPersistencePort, times(2)).save(refreshTokenCaptor.capture());
        List<RefreshToken> savedTokens = refreshTokenCaptor.getAllValues();
        RefreshToken revokedOldToken = savedTokens.get(0);
        RefreshToken newToken = savedTokens.get(1);

        assertTrue(revokedOldToken.isRevoked());
        assertFalse(newToken.isRevoked());
        assertEquals(user.getId(), newToken.getUserId());
        assertNotEquals(oldRefreshRaw, newToken.getToken());
        assertEquals(hashRefreshToken(response.refreshToken()), newToken.getToken());

        assertEquals("new-access", response.accessToken());
        assertNotEquals(newToken.getToken(), response.refreshToken());
    }

    @Test
    void getProfileReturnsStoredUser() {
        UUID userId = UUID.randomUUID();
        User user = User.restore(userId, "user@example.com", "hashed", "Juan Perez", "900000000");
        when(IUserPort.findById(userId)).thenReturn(Optional.of(user));

        var profile = authService.getProfile(userId);

        assertEquals(userId, profile.id());
        assertEquals("user@example.com", profile.email());
        assertEquals("Juan Perez", profile.name());
        assertEquals("900000000", profile.phone());
    }

    @Test
    void getProfileThrowsWhenUserIsMissing() {
        UUID userId = UUID.randomUUID();
        when(IUserPort.findById(userId)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.getProfile(userId));
    }

    @Test
    void updateProfileRenamesUserAndKeepsEmail() {
        UUID userId = UUID.randomUUID();
        User user = User.restore(userId, "user@example.com", "hashed", "Juan Perez", "900000000");
        when(IUserPort.findById(userId)).thenReturn(Optional.of(user));
        when(IUserPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var profile = authService.updateProfile(userId, "Juana Perez");

        verify(IUserPort).save(userCaptor.capture());
        assertEquals("Juana Perez", userCaptor.getValue().getName());
        assertEquals("user@example.com", userCaptor.getValue().getEmail());
        assertEquals("Juana Perez", profile.name());
    }

    @Test
    void updatePasswordEncodesNewPasswordWhenCurrentMatches() {
        UUID userId = UUID.randomUUID();
        User user = User.restore(userId, "user@example.com", "old-hash", "Juan Perez", "900000000");
        when(IUserPort.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("actual123", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("nueva1234")).thenReturn("new-hash");

        authService.updatePassword(userId, "actual123", "nueva1234");

        verify(IUserPort).save(userCaptor.capture());
        assertEquals("new-hash", userCaptor.getValue().getPassword());
    }

    @Test
    void updatePasswordThrowsWhenCurrentPasswordDoesNotMatch() {
        UUID userId = UUID.randomUUID();
        User user = User.restore(userId, "user@example.com", "old-hash", "Juan Perez", "900000000");
        when(IUserPort.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("incorrecta", "old-hash")).thenReturn(false);

        assertThrows(
                InvalidAuthRequestException.class,
                () -> authService.updatePassword(userId, "incorrecta", "nueva1234")
        );

        verify(IUserPort, never()).save(any(User.class));
    }

    private static String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }
}
