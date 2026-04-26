package com.legalfam.backend.auth;

import com.legalfam.backend.auth.exception.EmailAlreadyExistsException;
import com.legalfam.backend.auth.exception.InvalidCredentialsException;
import com.legalfam.backend.auth.exception.InvalidRefreshTokenException;
import com.legalfam.backend.auth.dto.TokenResponse;
import com.legalfam.backend.auth.token.RefreshToken;
import com.legalfam.backend.auth.token.RefreshTokenRepository;
import com.legalfam.backend.user.User;
import com.legalfam.backend.user.UserRepository;
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
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @org.springframework.beans.factory.annotation.Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Transactional
    public TokenResponse signup(String email, String rawPassword, String name, String phone) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setName(name);
        user.setPhone(phone);

        User savedUser = userRepository.save(user);
        return issueTokens(savedUser);
    }

    @Transactional
    public TokenResponse login(String email, String rawPassword) {
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(String refreshTokenValue) {
        String tokenHash = hashRefreshToken(refreshTokenValue);
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(tokenHash)
                .or(() -> refreshTokenRepository.findByToken(refreshTokenValue))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (refreshToken.isRevoked() || refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return issueTokens(refreshToken.getUser());
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = createRefreshToken(user);
        return new TokenResponse(accessToken, refreshToken, "Bearer", jwtService.getAccessTokenExpirationSeconds());
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
        refreshToken.setUser(user);

        refreshTokenRepository.save(refreshToken);
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
