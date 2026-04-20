package com.mtsaas.backend.infrastructure.repository;

import com.mtsaas.backend.domain.EmailVerificationToken;
import com.mtsaas.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {
    
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
