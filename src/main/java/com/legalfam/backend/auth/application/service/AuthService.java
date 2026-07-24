package com.legalfam.backend.auth.application.service;

import com.legalfam.backend.auth.application.port.in.IAuthUseCase;
import com.legalfam.backend.auth.application.port.out.IAccessTokenPort;
import com.legalfam.backend.auth.application.port.out.IAuthEventPublisherPort;
import com.legalfam.backend.auth.application.port.out.IRefreshTokenPersistencePort;
import com.legalfam.backend.auth.application.port.out.IUserPort;
import com.legalfam.backend.common.identity.event.UserRegisteredEvent;
import com.legalfam.backend.auth.domain.exception.EmailAlreadyExistsException;
import com.legalfam.backend.auth.domain.exception.InvalidAuthRequestException;
import com.legalfam.backend.auth.domain.exception.InvalidCredentialsException;
import com.legalfam.backend.auth.domain.exception.InvalidRefreshTokenException;
import com.legalfam.backend.auth.application.dto.TokenResponse;
import com.legalfam.backend.auth.application.dto.UserResponse;
import com.legalfam.backend.auth.domain.model.RefreshToken;
import com.legalfam.backend.auth.domain.model.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements IAuthUseCase {

    private final IUserPort IUserPort;
    private final IRefreshTokenPersistencePort IRefreshTokenPersistencePort;
    private final PasswordEncoder passwordEncoder;
    private final IAccessTokenPort IAccessTokenPort;
    private final IAuthEventPublisherPort IAuthEventPublisherPort;
    private final long refreshTokenExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            IUserPort IUserPort,
            IRefreshTokenPersistencePort IRefreshTokenPersistencePort,
            PasswordEncoder passwordEncoder,
            IAccessTokenPort IAccessTokenPort,
            IAuthEventPublisherPort IAuthEventPublisherPort,
            AuthTokenProperties authTokenProperties
    ) {
        this.IUserPort = IUserPort;
        this.IRefreshTokenPersistencePort = IRefreshTokenPersistencePort;
        this.passwordEncoder = passwordEncoder;
        this.IAccessTokenPort = IAccessTokenPort;
        this.IAuthEventPublisherPort = IAuthEventPublisherPort;
        this.refreshTokenExpirationMs = authTokenProperties.refreshTokenExpirationMs();
    }

    @Override
    @Transactional
    public TokenResponse signup(String email, String rawPassword, String name, String phone) {
        if (IUserPort.existsByEmail(email)) {
            throw EmailAlreadyExistsException.forEmail(email);
        }

        User user = User.create(email, passwordEncoder.encode(rawPassword), name, phone);

        User savedUser = IUserPort.save(user);
        IAuthEventPublisherPort.publishUserRegistered(new UserRegisteredEvent(savedUser.getId()));
        return issueTokens(savedUser);
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

        return issueTokens(user);
    }

    @Override
    @Transactional
    public TokenResponse refresh(String refreshTokenValue) {
        String tokenHash = hashRefreshToken(refreshTokenValue);
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

    private User requireUser(UUID userId) {
        return IUserPort.findById(userId).orElseThrow(InvalidCredentialsException::invalidCredentials);
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getPhone());
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
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        String tokenValue = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = hashRefreshToken(tokenValue);

        RefreshToken refreshToken = RefreshToken.issue(
                tokenHash,
                user.getId(),
                Instant.now().plusMillis(refreshTokenExpirationMs)
        );

        IRefreshTokenPersistencePort.save(refreshToken);
        return tokenValue;
    }

    private String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }
}
