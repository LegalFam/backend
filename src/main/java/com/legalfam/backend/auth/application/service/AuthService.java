package com.legalfam.backend.auth.application.service;

import com.legalfam.backend.auth.application.port.in.IAuthUseCase;
import com.legalfam.backend.auth.application.port.out.IAccessTokenPort;
import com.legalfam.backend.auth.application.port.out.IAuthEventPublisherPort;
import com.legalfam.backend.auth.application.port.out.IOneTimeTokenPersistencePort;
import com.legalfam.backend.auth.application.port.out.IRefreshTokenPersistencePort;
import com.legalfam.backend.auth.application.port.out.IUserPort;
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
import com.legalfam.backend.auth.domain.token.SecureTokenGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements IAuthUseCase {

    private final IUserPort IUserPort;
    private final IRefreshTokenPersistencePort IRefreshTokenPersistencePort;
    private final IOneTimeTokenPersistencePort IOneTimeTokenPersistencePort;
    private final PasswordEncoder passwordEncoder;
    private final IAccessTokenPort IAccessTokenPort;
    private final IAuthEventPublisherPort IAuthEventPublisherPort;
    private final long refreshTokenExpirationMs;
    private final long emailVerificationExpirationMs;
    private final long passwordResetExpirationMs;
    private final long mailResendCooldownMs;

    public AuthService(
            IUserPort IUserPort,
            IRefreshTokenPersistencePort IRefreshTokenPersistencePort,
            IOneTimeTokenPersistencePort IOneTimeTokenPersistencePort,
            PasswordEncoder passwordEncoder,
            IAccessTokenPort IAccessTokenPort,
            IAuthEventPublisherPort IAuthEventPublisherPort,
            AuthTokenProperties authTokenProperties
    ) {
        this.IUserPort = IUserPort;
        this.IRefreshTokenPersistencePort = IRefreshTokenPersistencePort;
        this.IOneTimeTokenPersistencePort = IOneTimeTokenPersistencePort;
        this.passwordEncoder = passwordEncoder;
        this.IAccessTokenPort = IAccessTokenPort;
        this.IAuthEventPublisherPort = IAuthEventPublisherPort;
        this.refreshTokenExpirationMs = authTokenProperties.refreshTokenExpirationMs();
        this.emailVerificationExpirationMs = authTokenProperties.emailVerificationExpirationMs();
        this.passwordResetExpirationMs = authTokenProperties.passwordResetExpirationMs();
        this.mailResendCooldownMs = authTokenProperties.mailResendCooldownMs();
    }

    @Override
    @Transactional
    public UserResponse signup(String email, String rawPassword, String name, String phone) {
        if (IUserPort.existsByEmail(email)) {
            throw EmailAlreadyExistsException.forEmail(email);
        }

        User user = User.create(email, passwordEncoder.encode(rawPassword), name, phone);

        User savedUser = IUserPort.save(user);
        IAuthEventPublisherPort.publishUserRegistered(new UserRegisteredEvent(savedUser.getId()));
        return toUserResponse(savedUser);
    }

    @Override
    @Transactional
    public TokenResponse login(String email, String rawPassword) {
        User user = IUserPort
                .findByEmail(email)
                .orElseThrow(InvalidCredentialsException::invalidCredentials);

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw InvalidCredentialsException.invalidCredentials();
        }

        // Checked after the password so verification state is never disclosed without valid credentials.
        if (!user.isEmailVerified()) {
            throw EmailNotVerifiedException.forLogin();
        }

        return issueTokens(user);
    }

    @Override
    @Transactional
    public TokenResponse refresh(String refreshTokenValue) {
        String tokenHash = SecureTokenGenerator.hash(refreshTokenValue);
        RefreshToken refreshToken = IRefreshTokenPersistencePort
                .findByToken(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::invalidRefreshToken);

        if (!refreshToken.canBeRotatedAt(Instant.now())) {
            throw InvalidRefreshTokenException.invalidRefreshToken();
        }

        refreshToken.revoke();
        IRefreshTokenPersistencePort.save(refreshToken);

        User user = IUserPort.findById(refreshToken.getUserId())
                .orElseThrow(InvalidRefreshTokenException::invalidRefreshToken);
        return issueTokens(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        return toUserResponse(requireUser(userId));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UUID userId, String name) {
        User user = requireUser(userId);
        user.rename(name);
        return toUserResponse(IUserPort.save(user));
    }

    @Override
    @Transactional
    public void updatePassword(UUID userId, String currentRawPassword, String newRawPassword) {
        User user = requireUser(userId);

        if (!passwordEncoder.matches(currentRawPassword, user.getPassword())) {
            throw InvalidAuthRequestException.currentPasswordInvalid();
        }

        user.changePassword(passwordEncoder.encode(newRawPassword));
        IUserPort.save(user);
    }

    /*
     * REQUIRES_NEW, not the default REQUIRED: these run from an AFTER_COMMIT listener, where the
     * synchronization of the just-committed signup transaction is still bound to the thread.
     * REQUIRED would join that dead transaction and every write would fail with
     * "No active transaction". REQUIRES_NEW also guarantees the token row is committed before the
     * dispatcher hands the raw token to the (async) mail port.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AuthMailDispatch> issueEmailVerificationToken(UUID userId) {
        return IUserPort.findById(userId).flatMap(this::issueEmailVerificationToken);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AuthMailDispatch> issueEmailVerificationToken(String email) {
        return IUserPort.findByEmail(email).flatMap(this::issueEmailVerificationToken);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AuthMailDispatch> issuePasswordResetToken(String email) {
        // An unknown email is not an error: the endpoint must not reveal who is registered.
        Optional<User> user = IUserPort.findByEmail(email);
        if (user.isEmpty()) {
            return Optional.empty();
        }
        return issueToken(user.get(), OneTimeTokenPurpose.PASSWORD_RESET, passwordResetExpirationMs);
    }

    @Override
    @Transactional
    public void confirmEmailVerification(String rawToken) {
        Instant now = Instant.now();
        OneTimeToken token = consumeToken(
                rawToken,
                OneTimeTokenPurpose.EMAIL_VERIFICATION,
                now,
                InvalidAuthRequestException::verificationTokenInvalid
        );

        User user = IUserPort.findById(token.getUserId())
                .orElseThrow(InvalidAuthRequestException::verificationTokenInvalid);
        user.verifyEmail(now);
        IUserPort.save(user);
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newRawPassword) {
        Instant now = Instant.now();
        OneTimeToken token = consumeToken(
                rawToken,
                OneTimeTokenPurpose.PASSWORD_RESET,
                now,
                InvalidAuthRequestException::resetTokenInvalid
        );

        User user = IUserPort.findById(token.getUserId())
                .orElseThrow(InvalidAuthRequestException::resetTokenInvalid);
        user.changePassword(passwordEncoder.encode(newRawPassword));
        // Opening the emailed link proves ownership of the address.
        user.verifyEmail(now);
        IUserPort.save(user);

        IOneTimeTokenPersistencePort.consumeAllFor(user.getId(), OneTimeTokenPurpose.PASSWORD_RESET, now);
        IOneTimeTokenPersistencePort.consumeAllFor(user.getId(), OneTimeTokenPurpose.EMAIL_VERIFICATION, now);
        IRefreshTokenPersistencePort.revokeAllForUser(user.getId());
    }

    private Optional<AuthMailDispatch> issueEmailVerificationToken(User user) {
        if (user.isEmailVerified()) {
            return Optional.empty();
        }
        return issueToken(user, OneTimeTokenPurpose.EMAIL_VERIFICATION, emailVerificationExpirationMs);
    }

    private Optional<AuthMailDispatch> issueToken(User user, OneTimeTokenPurpose purpose, long expirationMs) {
        Instant now = Instant.now();

        if (isWithinResendCooldown(user.getId(), purpose, now)) {
            return Optional.empty();
        }

        // Only the newest link may work.
        IOneTimeTokenPersistencePort.consumeAllFor(user.getId(), purpose, now);

        String rawToken = SecureTokenGenerator.generateRawToken(SecureTokenGenerator.ONE_TIME_TOKEN_BYTES);
        IOneTimeTokenPersistencePort.save(OneTimeToken.issue(
                SecureTokenGenerator.hash(rawToken),
                purpose,
                user.getId(),
                now,
                now.plusMillis(expirationMs)
        ));

        return Optional.of(new AuthMailDispatch(
                user.getEmail(),
                user.getName(),
                rawToken,
                Duration.ofMillis(expirationMs).toMinutes()
        ));
    }

    private boolean isWithinResendCooldown(UUID userId, OneTimeTokenPurpose purpose, Instant now) {
        return IOneTimeTokenPersistencePort.findLatestIssuedAt(userId, purpose)
                .map(issuedAt -> issuedAt.plusMillis(mailResendCooldownMs).isAfter(now))
                .orElse(false);
    }

    private OneTimeToken consumeToken(
            String rawToken,
            OneTimeTokenPurpose purpose,
            Instant now,
            Supplier<RuntimeException> invalid
    ) {
        OneTimeToken token = IOneTimeTokenPersistencePort
                .findByHashAndPurpose(SecureTokenGenerator.hash(rawToken), purpose)
                .orElseThrow(invalid);

        if (!token.isUsableAt(now)) {
            throw invalid.get();
        }

        token.consume(now);
        IOneTimeTokenPersistencePort.save(token);
        return token;
    }

    private User requireUser(UUID userId) {
        return IUserPort.findById(userId).orElseThrow(InvalidCredentialsException::invalidCredentials);
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.isEmailVerified()
        );
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = IAccessTokenPort.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = createRefreshToken(user);
        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                IAccessTokenPort.getAccessTokenExpirationSeconds(),
                toUserResponse(user)
        );
    }

    private String createRefreshToken(User user) {
        String tokenValue = SecureTokenGenerator.generateRawToken(SecureTokenGenerator.REFRESH_TOKEN_BYTES);

        RefreshToken refreshToken = RefreshToken.issue(
                SecureTokenGenerator.hash(tokenValue),
                user.getId(),
                Instant.now().plusMillis(refreshTokenExpirationMs)
        );

        IRefreshTokenPersistencePort.save(refreshToken);
        return tokenValue;
    }
}
