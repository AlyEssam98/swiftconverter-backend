package com.mtsaas.backend.infrastructure.repository;

import com.mtsaas.backend.domain.CreditPurchase;
import com.mtsaas.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CreditPurchaseRepository extends JpaRepository<CreditPurchase, UUID> {
    
    /**
     * Find all credit purchases for a user ordered by expiry date
     */
    List<CreditPurchase> findByUserOrderByExpiryDateAsc(User user);

    /**
     * Find all valid (non-expired) credit purchases for a user
     */
    @Query("SELECT cp FROM CreditPurchase cp WHERE cp.user = :user AND cp.expired = false AND cp.expiryDate > :now")
    List<CreditPurchase> findValidPurchases(@Param("user") User user, @Param("now") LocalDateTime now);

    /**
     * Sum all valid credits for a user
     */
    @Query("SELECT COALESCE(SUM(cp.creditAmount), 0) FROM CreditPurchase cp WHERE cp.user = :user AND cp.expired = false AND cp.expiryDate > :now")
    Long getAvailableCredits(@Param("user") User user, @Param("now") LocalDateTime now);

    /**
     * Find expired purchases for cleanup
     */
    @Query("SELECT cp FROM CreditPurchase cp WHERE cp.user = :user AND cp.expiryDate <= :now AND cp.expired = false")
    List<CreditPurchase> findExpiredPurchases(@Param("user") User user, @Param("now") LocalDateTime now);

    /**
     * Find all purchases by user
     */
    List<CreditPurchase> findByUser(User user);
}
