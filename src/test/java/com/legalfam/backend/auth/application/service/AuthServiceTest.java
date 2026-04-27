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

import com.legalfam.backend.auth.application.port.out.AccessTokenPort;
import com.legalfam.backend.auth.application.port.out.AuthUserPort;
import com.legalfam.backend.auth.application.port.out.RefreshTokenPort;
import com.legalfam.backend.auth.domain.exception.EmailAlreadyExistsException;
import com.legalfam.backend.auth.domain.exception.InvalidCredentialsException;
import com.legalfam.backend.auth.domain.exception.InvalidRefreshTokenException;
import com.legalfam.backend.auth.application.dto.TokenResponse;
import com.legalfam.backend.auth.domain.model.RefreshToken;
import com.legalfam.backend.user.domain.model.User;
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
    private AuthUserPort authUserPort;
    @Mock
    private RefreshTokenPort refreshTokenPort;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AccessTokenPort accessTokenPort;

    @Captor
    private ArgumentCaptor<User> userCaptor;
    @Captor
    private ArgumentCaptor<RefreshToken> refreshTokenCaptor;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authUserPort,
                refreshTokenPort,
                passwordEncoder,
                accessTokenPort,
                REFRESH_EXPIRATION_MS
        );
    }

    @Test
    void signupThrowsWhenEmailAlreadyExists() {
        when(authUserPort.existsByEmail("user@example.com")).thenReturn(true);

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> authService.signup("user@example.com", "secret", "Juan", "900000000")
        );

        verify(authUserPort, never()).save(any(User.class));
    }

    @Test
    void signupCreatesUserAndReturnsTokens() {
        when(authUserPort.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed-password");
        when(authUserPort.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            setUserId(saved, UUID.randomUUID());
            return saved;
        });
        when(accessTokenPort.generateAccessToken(any(UUID.class), eq("user@example.com"))).thenReturn("access-123");
        when(accessTokenPort.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenPort.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TokenResponse response = authService.signup("user@example.com", "secret", "Juan", "900000000");

        verify(authUserPort).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("user@example.com", savedUser.getEmail());
        assertEquals("hashed-password", savedUser.getPassword());
        assertEquals("Juan", savedUser.getName());
        assertEquals("900000000", savedUser.getPhone());

        verify(refreshTokenPort).save(refreshTokenCaptor.capture());
        RefreshToken refreshToken = refreshTokenCaptor.getValue();
        assertNotNull(refreshToken.getToken());
        assertNotEquals(response.refreshToken(), refreshToken.getToken());
        assertEquals(hashRefreshToken(response.refreshToken()), refreshToken.getToken());
        assertFalse(refreshToken.isRevoked());
        assertEquals(savedUser, refreshToken.getUser());
        assertTrue(refreshToken.getExpiresAt().isAfter(Instant.now()));

        assertEquals("access-123", response.accessToken());
        assertNotEquals(refreshToken.getToken(), response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900L, response.expiresIn());
    }

    @Test
    void loginThrowsWhenUserDoesNotExist() {
        when(authUserPort.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login("missing@example.com", "secret")
        );
    }

    @Test
    void loginThrowsWhenPasswordDoesNotMatch() {
        User user = createUser("user@example.com", "stored-hash");
        when(authUserPort.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login("user@example.com", "wrong-password")
        );
    }

    @Test
    void refreshThrowsWhenTokenIsRevoked() {
        User user = createUser("user@example.com", "stored-hash");
        String rawToken = "revoked-token";
        RefreshToken existing = createRefreshToken(hashRefreshToken(rawToken), user, Instant.now().plusSeconds(60), true);
        when(refreshTokenPort.findByToken(hashRefreshToken(rawToken))).thenReturn(Optional.of(existing));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(rawToken)
        );

        verify(refreshTokenPort, never()).save(any(RefreshToken.class));
    }

    @Test
    void refreshThrowsWhenTokenIsExpired() {
        User user = createUser("user@example.com", "stored-hash");
        String rawToken = "expired-token";
        RefreshToken existing = createRefreshToken(hashRefreshToken(rawToken), user, Instant.now().minusSeconds(60), false);
        when(refreshTokenPort.findByToken(hashRefreshToken(rawToken))).thenReturn(Optional.of(existing));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(rawToken)
        );

        verify(refreshTokenPort, never()).save(any(RefreshToken.class));
    }

    @Test
    void refreshRevokesOldTokenAndIssuesNewOne() {
        User user = createUser("user@example.com", "stored-hash");
        String oldRefreshRaw = "old-refresh";
        String oldRefreshHashed = hashRefreshToken(oldRefreshRaw);
        RefreshToken existing = createRefreshToken(oldRefreshHashed, user, Instant.now().plusSeconds(60), false);
        when(refreshTokenPort.findByToken(oldRefreshHashed)).thenReturn(Optional.of(existing));
        when(refreshTokenPort.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accessTokenPort.generateAccessToken(any(UUID.class), eq("user@example.com"))).thenReturn("new-access");
        when(accessTokenPort.getAccessTokenExpirationSeconds()).thenReturn(900L);

        TokenResponse response = authService.refresh(oldRefreshRaw);

        verify(refreshTokenPort, times(2)).save(refreshTokenCaptor.capture());
        List<RefreshToken> savedTokens = refreshTokenCaptor.getAllValues();
        RefreshToken revokedOldToken = savedTokens.get(0);
        RefreshToken newToken = savedTokens.get(1);

        assertTrue(revokedOldToken.isRevoked());
        assertFalse(newToken.isRevoked());
        assertEquals(user, newToken.getUser());
        assertNotEquals(oldRefreshRaw, newToken.getToken());
        assertEquals(hashRefreshToken(response.refreshToken()), newToken.getToken());

        assertEquals("new-access", response.accessToken());
        assertNotEquals(newToken.getToken(), response.refreshToken());
    }

    @Test
    void refreshSupportsLegacyRawTokenStorage() {
        User user = createUser("user@example.com", "stored-hash");
        String legacyRawToken = "legacy-raw-token";
        RefreshToken existing = createRefreshToken(legacyRawToken, user, Instant.now().plusSeconds(60), false);
        when(refreshTokenPort.findByToken(hashRefreshToken(legacyRawToken))).thenReturn(Optional.empty());
        when(refreshTokenPort.findByToken(legacyRawToken)).thenReturn(Optional.of(existing));
        when(refreshTokenPort.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accessTokenPort.generateAccessToken(any(UUID.class), eq("user@example.com"))).thenReturn("new-access");
        when(accessTokenPort.getAccessTokenExpirationSeconds()).thenReturn(900L);

        TokenResponse response = authService.refresh(legacyRawToken);

        verify(refreshTokenPort, times(2)).save(refreshTokenCaptor.capture());
        List<RefreshToken> savedTokens = refreshTokenCaptor.getAllValues();
        RefreshToken revokedOldToken = savedTokens.get(0);
        RefreshToken newToken = savedTokens.get(1);

        assertTrue(revokedOldToken.isRevoked());
        assertEquals(legacyRawToken, revokedOldToken.getToken());
        assertEquals(hashRefreshToken(response.refreshToken()), newToken.getToken());
        assertEquals("new-access", response.accessToken());
    }

    private static User createUser(String email, String password) {
        User user = new User();
        setUserId(user, UUID.randomUUID());
        user.setEmail(email);
        user.setPassword(password);
        user.setName("Test User");
        user.setPhone("900000000");
        return user;
    }

    private static RefreshToken createRefreshToken(String token, User user, Instant expiresAt, boolean revoked) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(token);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(expiresAt);
        refreshToken.setRevoked(revoked);
        return refreshToken;
    }

    private static void setUserId(User user, UUID id) {
        try {
            java.lang.reflect.Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
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
