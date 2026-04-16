package com.legalfam.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.legalfam.backend.auth.exception.EmailAlreadyExistsException;
import com.legalfam.backend.auth.exception.InvalidCredentialsException;
import com.legalfam.backend.auth.exception.InvalidRefreshTokenException;
import com.legalfam.backend.auth.dto.TokenResponse;
import com.legalfam.backend.auth.token.RefreshToken;
import com.legalfam.backend.auth.token.RefreshTokenRepository;
import com.legalfam.backend.user.User;
import com.legalfam.backend.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @Captor
    private ArgumentCaptor<User> userCaptor;
    @Captor
    private ArgumentCaptor<RefreshToken> refreshTokenCaptor;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                REFRESH_EXPIRATION_MS
        );
    }

    @Test
    void signupThrowsWhenEmailAlreadyExists() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> authService.signup("user@example.com", "secret")
        );

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void signupCreatesUserAndReturnsTokens() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken("user@example.com")).thenReturn("access-123");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TokenResponse response = authService.signup("user@example.com", "secret");

        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("user@example.com", savedUser.getEmail());
        assertEquals("hashed-password", savedUser.getPassword());

        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken refreshToken = refreshTokenCaptor.getValue();
        assertNotNull(refreshToken.getToken());
        assertFalse(refreshToken.isRevoked());
        assertEquals(savedUser, refreshToken.getUser());
        assertTrue(refreshToken.getExpiresAt().isAfter(Instant.now()));

        assertEquals("access-123", response.accessToken());
        assertEquals(refreshToken.getToken(), response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(900L, response.expiresIn());
    }

    @Test
    void loginThrowsWhenUserDoesNotExist() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login("missing@example.com", "secret")
        );
    }

    @Test
    void loginThrowsWhenPasswordDoesNotMatch() {
        User user = createUser("user@example.com", "stored-hash");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login("user@example.com", "wrong-password")
        );
    }

    @Test
    void refreshThrowsWhenTokenIsRevoked() {
        User user = createUser("user@example.com", "stored-hash");
        RefreshToken existing = createRefreshToken("revoked-token", user, Instant.now().plusSeconds(60), true);
        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(existing));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh("revoked-token")
        );

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void refreshThrowsWhenTokenIsExpired() {
        User user = createUser("user@example.com", "stored-hash");
        RefreshToken existing = createRefreshToken("expired-token", user, Instant.now().minusSeconds(60), false);
        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(existing));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh("expired-token")
        );

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void refreshRevokesOldTokenAndIssuesNewOne() {
        User user = createUser("user@example.com", "stored-hash");
        RefreshToken existing = createRefreshToken("old-refresh", user, Instant.now().plusSeconds(60), false);
        when(refreshTokenRepository.findByToken("old-refresh")).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken("user@example.com")).thenReturn("new-access");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        TokenResponse response = authService.refresh("old-refresh");

        verify(refreshTokenRepository, times(2)).save(refreshTokenCaptor.capture());
        List<RefreshToken> savedTokens = refreshTokenCaptor.getAllValues();
        RefreshToken revokedOldToken = savedTokens.get(0);
        RefreshToken newToken = savedTokens.get(1);

        assertTrue(revokedOldToken.isRevoked());
        assertFalse(newToken.isRevoked());
        assertEquals(user, newToken.getUser());
        assertNotEquals("old-refresh", newToken.getToken());

        assertEquals("new-access", response.accessToken());
        assertEquals(newToken.getToken(), response.refreshToken());
    }

    private static User createUser(String email, String password) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(password);
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
}
