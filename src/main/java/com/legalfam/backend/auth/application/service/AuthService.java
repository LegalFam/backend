package com.legalfam.backend.auth.application.service;

import com.legalfam.backend.auth.application.port.in.IAuthUseCase;
import com.legalfam.backend.auth.application.port.out.IAccessTokenPort;
import com.legalfam.backend.auth.application.port.out.IAuthEventPublisherPort;
import com.legalfam.backend.auth.application.port.out.IRefreshTokenPort;
import com.legalfam.backend.auth.application.port.out.IUserPort;
import com.legalfam.backend.common.identity.event.UserRegisteredEvent;
import com.legalfam.backend.auth.domain.exception.EmailAlreadyExistsException;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements IAuthUseCase {

    private final IUserPort IUserPort;
    private final IRefreshTokenPort IRefreshTokenPort;
    private final PasswordEncoder passwordEncoder;
    private final IAccessTokenPort IAccessTokenPort;
    private final IAuthEventPublisherPort IAuthEventPublisherPort;
    private final long refreshTokenExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            IUserPort IUserPort,
            IRefreshTokenPort IRefreshTokenPort,
            PasswordEncoder passwordEncoder,
            IAccessTokenPort IAccessTokenPort,
            IAuthEventPublisherPort IAuthEventPublisherPort,
            AuthTokenProperties authTokenProperties
    ) {
        this.IUserPort = IUserPort;
        this.IRefreshTokenPort = IRefreshTokenPort;
        this.passwordEncoder = passwordEncoder;
        this.IAccessTokenPort = IAccessTokenPort;
        this.IAuthEventPublisherPort = IAuthEventPublisherPort;
        this.refreshTokenExpirationMs = authTokenProperties.refreshTokenExpirationMs();
    }

    @Override
    @Transactional
    public TokenResponse signup(String email, String rawPassword, String name, String phone) {
        if (IUserPort.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
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
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return issueTokens(user);
    }

    @Override
    @Transactional
    public TokenResponse refresh(String refreshTokenValue) {
        String tokenHash = hashRefreshToken(refreshTokenValue);
        RefreshToken refreshToken = IRefreshTokenPort
                .findByToken(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!refreshToken.canBeRotatedAt(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        refreshToken.revoke();
        IRefreshTokenPort.save(refreshToken);

        User user = IUserPort.findById(refreshToken.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);
        return issueTokens(user);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = IAccessTokenPort.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = createRefreshToken(user);
        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                IAccessTokenPort.getAccessTokenExpirationSeconds(),
                new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getPhone())
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

        IRefreshTokenPort.save(refreshToken);
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
