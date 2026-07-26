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
import com.legalfam.backend.auth.application.port.out.IOneTimeTokenPersistencePort;
import com.legalfam.backend.auth.application.port.out.IUserPort;
import com.legalfam.backend.auth.application.port.out.IRefreshTokenPersistencePort;
import com.legalfam.backend.common.identity.event.UserRegisteredEvent;
import com.legalfam.backend.auth.domain.exception.EmailAlreadyExistsException;
import com.legalfam.backend.auth.domain.exception.EmailNotVerifiedException;
import com.legalfam.backend.auth.domain.exception.InvalidAuthRequestException;
import com.legalfam.backend.auth.domain.exception.InvalidCredentialsException;
import com.legalfam.backend.auth.domain.exception.InvalidRefreshTokenException;
import com.legalfam.backend.auth.application.dto.AuthMailDispatch;
import com.legalfam.backend.auth.application.dto.TokenResponse;
import com.legalfam.backend.auth.application.dto.UserResponse;
import com.legalfam.backend.auth.domain.model.OneTimeToken;
import com.legalfam.backend.auth.domain.model.OneTimeTokenPurpose;
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
    private static final long EMAIL_VERIFICATION_EXPIRATION_MS = 86_400_000L;
    private static final long PASSWORD_RESET_EXPIRATION_MS = 3_600_000L;
    private static final long MAIL_RESEND_COOLDOWN_MS = 60_000L;

    @Mock
    private IUserPort IUserPort;
    @Mock
    private IRefreshTokenPersistencePort IRefreshTokenPersistencePort;
    @Mock
    private IOneTimeTokenPersistencePort IOneTimeTokenPersistencePort;
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
    @Captor
    private ArgumentCaptor<OneTimeToken> oneTimeTokenCaptor;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                IUserPort,
                IRefreshTokenPersistencePort,
                IOneTimeTokenPersistencePort,
                passwordEncoder,
                IAccessTokenPort,
                IAuthEventPublisherPort,
                new AuthTokenProperties(
                        REFRESH_EXPIRATION_MS,
                        EMAIL_VERIFICATION_EXPIRATION_MS,
                        PASSWORD_RESET_EXPIRATION_MS,
                        MAIL_RESEND_COOLDOWN_MS
                )
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
    void signupCreatesUnverifiedUserWithoutTokens() {
        UUID userId = UUID.randomUUID();
        when(IUserPort.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("hashed-password");
        when(IUserPort.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return User.restore(
                    userId,
                    user.getEmail(),
                    user.getPassword(),
                    user.getName(),
                    user.getPhone(),
                    user.isEmailVerified(),
                    user.getEmailVerifiedAt()
            );
        });

        UserResponse response = authService.signup("user@example.com", "secret", "Juan", "900000000");

        verify(IUserPort).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("user@example.com", savedUser.getEmail());
        assertEquals("hashed-password", savedUser.getPassword());
        assertEquals("Juan", savedUser.getName());
        assertEquals("900000000", savedUser.getPhone());
        assertFalse(savedUser.isEmailVerified());
        verify(IAuthEventPublisherPort).publishUserRegistered(new UserRegisteredEvent(userId));

        assertEquals(userId, response.id());
        assertEquals("user@example.com", response.email());
        assertFalse(response.emailVerified());

        // No session is established until the email is verified.
        verify(IAccessTokenPort, never()).generateAccessToken(any(UUID.class), any(String.class));
        verify(IRefreshTokenPersistencePort, never()).save(any(RefreshToken.class));
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
        User user = verifiedUser(UUID.randomUUID(), "user@example.com", "stored-hash");
        when(IUserPort.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login("user@example.com", "wrong-password")
        );
    }

    @Test
    void loginThrowsEmailNotVerifiedWhenEmailIsUnverified() {
        User user = unverifiedUser(UUID.randomUUID(), "user@example.com", "stored-hash");
        when(IUserPort.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "stored-hash")).thenReturn(true);

        assertThrows(
                EmailNotVerifiedException.class,
                () -> authService.login("user@example.com", "secret")
        );

        verify(IAccessTokenPort, never()).generateAccessToken(any(UUID.class), any(String.class));
    }

    @Test
    void loginChecksPasswordBeforeVerificationState() {
        User user = unverifiedUser(UUID.randomUUID(), "user@example.com", "stored-hash");
        when(IUserPort.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        // A wrong password must not reveal that the account exists but is unverified.
        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login("user@example.com", "wrong-password")
        );
    }

    @Test
    void refreshThrowsWhenTokenIsRevoked() {
        User user = verifiedUser(UUID.randomUUID(), "user@example.com", "stored-hash");
        String rawToken = "revoked-token";
        RefreshToken existing = RefreshToken.issue(hashToken(rawToken), user.getId(), Instant.now().plusSeconds(60));
        existing.revoke();
        when(IRefreshTokenPersistencePort.findByToken(hashToken(rawToken))).thenReturn(Optional.of(existing));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(rawToken)
        );

        verify(IRefreshTokenPersistencePort, never()).save(any(RefreshToken.class));
    }

    @Test
    void refreshThrowsWhenTokenIsExpired() {
        User user = verifiedUser(UUID.randomUUID(), "user@example.com", "stored-hash");
        String rawToken = "expired-token";
        RefreshToken existing = RefreshToken.issue(hashToken(rawToken), user.getId(), Instant.now().minusSeconds(60));
        when(IRefreshTokenPersistencePort.findByToken(hashToken(rawToken))).thenReturn(Optional.of(existing));

        assertThrows(
                InvalidRefreshTokenException.class,
                () -> authService.refresh(rawToken)
        );

        verify(IRefreshTokenPersistencePort, never()).save(any(RefreshToken.class));
    }

    @Test
    void refreshThrowsWhenTokenDoesNotExist() {
        String rawToken = "missing-token";
        when(IRefreshTokenPersistencePort.findByToken(hashToken(rawToken))).thenReturn(Optional.empty());

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
        String oldRefreshHashed = hashToken(oldRefreshRaw);
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
        User user = verifiedUser(UUID.randomUUID(), "user@example.com", "stored-hash");
        String oldRefreshRaw = "old-refresh";
        String oldRefreshHashed = hashToken(oldRefreshRaw);
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
        assertEquals(hashToken(response.refreshToken()), newToken.getToken());

        assertEquals("new-access", response.accessToken());
        assertNotEquals(newToken.getToken(), response.refreshToken());
    }

    @Test
    void getProfileReturnsStoredUser() {
        UUID userId = UUID.randomUUID();
        User user = verifiedUser(userId, "user@example.com", "hashed");
        when(IUserPort.findById(userId)).thenReturn(Optional.of(user));

        var profile = authService.getProfile(userId);

        assertEquals(userId, profile.id());
        assertEquals("user@example.com", profile.email());
        assertEquals("Juan Perez", profile.name());
        assertEquals("900000000", profile.phone());
        assertTrue(profile.emailVerified());
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
        User user = verifiedUser(userId, "user@example.com", "hashed");
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
        User user = verifiedUser(userId, "user@example.com", "old-hash");
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
        User user = verifiedUser(userId, "user@example.com", "old-hash");
        when(IUserPort.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("incorrecta", "old-hash")).thenReturn(false);

        assertThrows(
                InvalidAuthRequestException.class,
                () -> authService.updatePassword(userId, "incorrecta", "nueva1234")
        );

        verify(IUserPort, never()).save(any(User.class));
    }

    @Test
    void issueEmailVerificationTokenStoresHashAndInvalidatesPreviousTokens() {
        UUID userId = UUID.randomUUID();
        User user = unverifiedUser(userId, "user@example.com", "hashed");
        when(IUserPort.findById(userId)).thenReturn(Optional.of(user));
        when(IOneTimeTokenPersistencePort.findLatestIssuedAt(userId, OneTimeTokenPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.empty());

        Optional<AuthMailDispatch> dispatch = authService.issueEmailVerificationToken(userId);

        assertTrue(dispatch.isPresent());
        assertEquals("user@example.com", dispatch.get().email());
        assertNotNull(dispatch.get().rawToken());
        assertEquals(1440L, dispatch.get().expiresInMinutes());

        verify(IOneTimeTokenPersistencePort)
                .consumeAllFor(eq(userId), eq(OneTimeTokenPurpose.EMAIL_VERIFICATION), any(Instant.class));
        verify(IOneTimeTokenPersistencePort).save(oneTimeTokenCaptor.capture());
        OneTimeToken saved = oneTimeTokenCaptor.getValue();
        assertEquals(OneTimeTokenPurpose.EMAIL_VERIFICATION, saved.getPurpose());
        assertEquals(userId, saved.getUserId());
        // Only the digest is persisted; the raw token goes out by email.
        assertNotEquals(dispatch.get().rawToken(), saved.getTokenHash());
        assertEquals(hashToken(dispatch.get().rawToken()), saved.getTokenHash());
    }

    @Test
    void issueEmailVerificationTokenReturnsEmptyForUnknownUser() {
        when(IUserPort.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertTrue(authService.issueEmailVerificationToken("missing@example.com").isEmpty());

        verify(IOneTimeTokenPersistencePort, never()).save(any(OneTimeToken.class));
    }

    @Test
    void issueEmailVerificationTokenReturnsEmptyForAlreadyVerifiedUser() {
        User user = verifiedUser(UUID.randomUUID(), "user@example.com", "hashed");
        when(IUserPort.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertTrue(authService.issueEmailVerificationToken("user@example.com").isEmpty());

        verify(IOneTimeTokenPersistencePort, never()).save(any(OneTimeToken.class));
    }

    @Test
    void issueEmailVerificationTokenReturnsEmptyWithinResendCooldown() {
        UUID userId = UUID.randomUUID();
        User user = unverifiedUser(userId, "user@example.com", "hashed");
        when(IUserPort.findById(userId)).thenReturn(Optional.of(user));
        when(IOneTimeTokenPersistencePort.findLatestIssuedAt(userId, OneTimeTokenPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(Instant.now().minusMillis(MAIL_RESEND_COOLDOWN_MS / 2)));

        assertTrue(authService.issueEmailVerificationToken(userId).isEmpty());

        verify(IOneTimeTokenPersistencePort, never()).save(any(OneTimeToken.class));
        verify(IOneTimeTokenPersistencePort, never())
                .consumeAllFor(any(UUID.class), any(OneTimeTokenPurpose.class), any(Instant.class));
    }

    @Test
    void issuePasswordResetTokenReturnsEmptyForUnknownEmailWithoutThrowing() {
        when(IUserPort.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertTrue(authService.issuePasswordResetToken("missing@example.com").isEmpty());

        verify(IOneTimeTokenPersistencePort, never()).save(any(OneTimeToken.class));
    }

    @Test
    void issuePasswordResetTokenWorksForUnverifiedUser() {
        UUID userId = UUID.randomUUID();
        User user = unverifiedUser(userId, "user@example.com", "hashed");
        when(IUserPort.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(IOneTimeTokenPersistencePort.findLatestIssuedAt(userId, OneTimeTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.empty());

        Optional<AuthMailDispatch> dispatch = authService.issuePasswordResetToken("user@example.com");

        assertTrue(dispatch.isPresent());
        assertEquals(60L, dispatch.get().expiresInMinutes());
        verify(IOneTimeTokenPersistencePort).save(oneTimeTokenCaptor.capture());
        assertEquals(OneTimeTokenPurpose.PASSWORD_RESET, oneTimeTokenCaptor.getValue().getPurpose());
    }

    @Test
    void confirmEmailVerificationMarksUserVerifiedAndConsumesToken() {
        UUID userId = UUID.randomUUID();
        String rawToken = "verification-token";
        User user = unverifiedUser(userId, "user@example.com", "hashed");
        OneTimeToken token = OneTimeToken.issue(
                hashToken(rawToken),
                OneTimeTokenPurpose.EMAIL_VERIFICATION,
                userId,
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        when(IOneTimeTokenPersistencePort.findByHashAndPurpose(
                hashToken(rawToken), OneTimeTokenPurpose.EMAIL_VERIFICATION)).thenReturn(Optional.of(token));
        when(IUserPort.findById(userId)).thenReturn(Optional.of(user));

        authService.confirmEmailVerification(rawToken);

        verify(IOneTimeTokenPersistencePort).save(oneTimeTokenCaptor.capture());
        assertNotNull(oneTimeTokenCaptor.getValue().getConsumedAt());
        verify(IUserPort).save(userCaptor.capture());
        assertTrue(userCaptor.getValue().isEmailVerified());
        assertNotNull(userCaptor.getValue().getEmailVerifiedAt());
    }

    @Test
    void confirmEmailVerificationThrowsWhenTokenIsExpired() {
        String rawToken = "expired-token";
        OneTimeToken token = OneTimeToken.issue(
                hashToken(rawToken),
                OneTimeTokenPurpose.EMAIL_VERIFICATION,
                UUID.randomUUID(),
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(60)
        );
        when(IOneTimeTokenPersistencePort.findByHashAndPurpose(
                hashToken(rawToken), OneTimeTokenPurpose.EMAIL_VERIFICATION)).thenReturn(Optional.of(token));

        assertThrows(
                InvalidAuthRequestException.class,
                () -> authService.confirmEmailVerification(rawToken)
        );

        verify(IUserPort, never()).save(any(User.class));
    }

    @Test
    void confirmEmailVerificationThrowsWhenTokenWasAlreadyConsumed() {
        String rawToken = "used-token";
        OneTimeToken token = OneTimeToken.issue(
                hashToken(rawToken),
                OneTimeTokenPurpose.EMAIL_VERIFICATION,
                UUID.randomUUID(),
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        token.consume(Instant.now());
        when(IOneTimeTokenPersistencePort.findByHashAndPurpose(
                hashToken(rawToken), OneTimeTokenPurpose.EMAIL_VERIFICATION)).thenReturn(Optional.of(token));

        assertThrows(
                InvalidAuthRequestException.class,
                () -> authService.confirmEmailVerification(rawToken)
        );

        verify(IUserPort, never()).save(any(User.class));
    }

    @Test
    void confirmEmailVerificationThrowsWhenTokenIsUnknown() {
        when(IOneTimeTokenPersistencePort.findByHashAndPurpose(
                hashToken("ghost"), OneTimeTokenPurpose.EMAIL_VERIFICATION)).thenReturn(Optional.empty());

        assertThrows(
                InvalidAuthRequestException.class,
                () -> authService.confirmEmailVerification("ghost")
        );
    }

    @Test
    void resetPasswordUpdatesPasswordAndRevokesEverySession() {
        UUID userId = UUID.randomUUID();
        String rawToken = "reset-token";
        User user = unverifiedUser(userId, "user@example.com", "old-hash");
        OneTimeToken token = OneTimeToken.issue(
                hashToken(rawToken),
                OneTimeTokenPurpose.PASSWORD_RESET,
                userId,
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        when(IOneTimeTokenPersistencePort.findByHashAndPurpose(
                hashToken(rawToken), OneTimeTokenPurpose.PASSWORD_RESET)).thenReturn(Optional.of(token));
        when(IUserPort.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("nueva1234")).thenReturn("new-hash");

        authService.resetPassword(rawToken, "nueva1234");

        verify(IUserPort).save(userCaptor.capture());
        assertEquals("new-hash", userCaptor.getValue().getPassword());
        // Opening the emailed link proves ownership of the address.
        assertTrue(userCaptor.getValue().isEmailVerified());

        verify(IOneTimeTokenPersistencePort).save(oneTimeTokenCaptor.capture());
        assertNotNull(oneTimeTokenCaptor.getValue().getConsumedAt());
        verify(IOneTimeTokenPersistencePort)
                .consumeAllFor(eq(userId), eq(OneTimeTokenPurpose.PASSWORD_RESET), any(Instant.class));
        verify(IOneTimeTokenPersistencePort)
                .consumeAllFor(eq(userId), eq(OneTimeTokenPurpose.EMAIL_VERIFICATION), any(Instant.class));
        verify(IRefreshTokenPersistencePort).revokeAllForUser(userId);
    }

    @Test
    void resetPasswordThrowsAndKeepsPasswordWhenTokenIsExpired() {
        String rawToken = "expired-reset";
        OneTimeToken token = OneTimeToken.issue(
                hashToken(rawToken),
                OneTimeTokenPurpose.PASSWORD_RESET,
                UUID.randomUUID(),
                Instant.now().minusSeconds(7200),
                Instant.now().minusSeconds(60)
        );
        when(IOneTimeTokenPersistencePort.findByHashAndPurpose(
                hashToken(rawToken), OneTimeTokenPurpose.PASSWORD_RESET)).thenReturn(Optional.of(token));

        assertThrows(
                InvalidAuthRequestException.class,
                () -> authService.resetPassword(rawToken, "nueva1234")
        );

        verify(IUserPort, never()).save(any(User.class));
        verify(IRefreshTokenPersistencePort, never()).revokeAllForUser(any(UUID.class));
    }

    private static User verifiedUser(UUID id, String email, String passwordHash) {
        return User.restore(id, email, passwordHash, "Juan Perez", "900000000", true, Instant.now());
    }

    private static User unverifiedUser(UUID id, String email, String passwordHash) {
        return User.restore(id, email, passwordHash, "Juan Perez", "900000000", false, null);
    }

    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }
}
