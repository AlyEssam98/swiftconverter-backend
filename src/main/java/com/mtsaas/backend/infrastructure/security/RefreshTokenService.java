package com.mtsaas.backend.infrastructure.security;

import com.mtsaas.backend.domain.RefreshToken;
import com.mtsaas.backend.domain.User;
import com.mtsaas.backend.infrastructure.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenGeneratorService tokenGeneratorService;

    @Value("${app.jwt.refresh-token-expiration-ms:1209600000}")
    private long refreshTokenExpirationMs;

    @Transactional
    public String issueToken(User user) {
        String rawToken = tokenGeneratorService.generateRawToken();
        String tokenHash = tokenGeneratorService.hashToken(rawToken);
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusNanos(refreshTokenExpirationMs * 1_000_000L))
                .build();
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findValidToken(String rawToken) {
        String tokenHash = tokenGeneratorService.hashToken(rawToken);
        LocalDateTime now = LocalDateTime.now();
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .filter(token -> !token.isRevoked())
                .filter(token -> !token.isExpired(now));
    }

    @Transactional
    public void revoke(RefreshToken refreshToken) {
        refreshToken.setRevokedAt(LocalDateTime.now());
        refreshToken.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public void revokeByRawToken(String rawToken) {
        String tokenHash = tokenGeneratorService.hashToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(this::revoke);
    }

    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.deleteAllByUser(user);
    }
}
