package com.mtsaas.backend.infrastructure.repository;

import com.mtsaas.backend.domain.Conversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ConversionRepository extends JpaRepository<Conversion, UUID> {
    List<Conversion> findByUserEmailOrderByCreatedAtDesc(String email);

    List<Conversion> findByUserIdOrderByCreatedAtDesc(UUID userId);

    long countByUserEmail(String email);

    long countByUserIdAndStatus(UUID userId, Conversion.Status status);

    long countByUserIdAndCreatedAtAfter(UUID userId, LocalDateTime date);

    List<Conversion> findTop5ByUserEmailOrderByCreatedAtDesc(String email);

    long countByIpAddressAndUserIsNull(String ipAddress);
}
