package com.mtsaas.backend.infrastructure.repository;

import com.mtsaas.backend.domain.RefreshToken;
import com.mtsaas.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteAllByUser(User user);

    void deleteByExpiresAtBefore(LocalDateTime threshold);
}
