package com.legalfam.backend.auth.application.service;

import com.legalfam.backend.auth.application.port.in.AuthUseCase;
import com.legalfam.backend.auth.application.port.out.AccessTokenPort;
import com.legalfam.backend.auth.application.port.out.RefreshTokenPort;
import com.legalfam.backend.auth.application.port.out.UserPort;
import com.legalfam.backend.auth.domain.exception.EmailAlreadyExistsException;
import com.legalfam.backend.auth.domain.exception.InvalidCredentialsException;
import com.legalfam.backend.auth.domain.exception.InvalidRefreshTokenException;
import com.legalfam.backend.auth.application.dto.TokenResponse;
import com.legalfam.backend.auth.application.dto.UserResponse;
import com.legalfam.backend.auth.domain.model.RefreshToken;
import com.legalfam.backend.auth.domain.model.User;
import com.legalfam.backend.payment.application.port.in.PaymentProvisioningUseCase;
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
public class AuthService implements AuthUseCase {

    private final UserPort userPort;
    private final RefreshTokenPort refreshTokenPort;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenPort accessTokenPort;
    private final PaymentProvisioningUseCase paymentProvisioningUseCase;
    private final long refreshTokenExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserPort userPort,
            RefreshTokenPort refreshTokenPort,
            PasswordEncoder passwordEncoder,
            AccessTokenPort accessTokenPort,
            PaymentProvisioningUseCase paymentProvisioningUseCase,
            AuthTokenProperties authTokenProperties
    ) {
        this.userPort = userPort;
        this.refreshTokenPort = refreshTokenPort;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenPort = accessTokenPort;
        this.paymentProvisioningUseCase = paymentProvisioningUseCase;
        this.refreshTokenExpirationMs = authTokenProperties.refreshTokenExpirationMs();
    }

    @Override
    @Transactional
    public TokenResponse signup(String email, String rawPassword, String name, String phone) {
        if (userPort.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setName(name);
        user.setPhone(phone);

        User savedUser = userPort.save(user);
        paymentProvisioningUseCase.provisionFreeSubscription(savedUser.getId());
        return issueTokens(savedUser);
    }

    @Override
    @Transactional
    public TokenResponse login(String email, String rawPassword) {
        User user = userPort
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
        RefreshToken refreshToken = refreshTokenPort
                .findByToken(tokenHash)
                .or(() -> refreshTokenPort.findByToken(refreshTokenValue))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        refreshToken.setRevoked(true);
        refreshTokenPort.save(refreshToken);

        User user = userPort.findById(refreshToken.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);
        return issueTokens(user);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = accessTokenPort.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = createRefreshToken(user);
        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                accessTokenPort.getAccessTokenExpirationSeconds(),
                new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getPhone())
        );
    }

    private String createRefreshToken(User user) {
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        String tokenValue = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = hashRefreshToken(tokenValue);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(tokenHash);
        refreshToken.setRevoked(false);
        refreshToken.setExpiresAt(Instant.now().plusMillis(refreshTokenExpirationMs));
        refreshToken.setUserId(user.getId());

        refreshTokenPort.save(refreshToken);
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
