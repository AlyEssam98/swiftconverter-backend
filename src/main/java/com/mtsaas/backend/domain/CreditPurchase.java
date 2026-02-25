package com.mtsaas.backend.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks individual credit purchases with their own 30-day expiry dates.
 * This allows users to have multiple purchases with staggered expiry dates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "credit_purchases", indexes = {
        @Index(name = "idx_credit_purchases_user_id", columnList = "user_id"),
        @Index(name = "idx_credit_purchases_expiry_date", columnList = "expiry_date")
})
public class CreditPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private long creditAmount;

    @Column(nullable = false)
    private String transactionId;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime purchasedAt;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    @Default
    @Column(nullable = false)
    private boolean expired = false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * Check if this credit bundle is still valid (not expired)
     */
    public boolean isValid() {
        return !expired && expiryDate != null && expiryDate.isAfter(LocalDateTime.now());
    }

    /**
     * Get days remaining until expiry
     */
    public Long getDaysUntilExpiry() {
        if (!isValid()) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), expiryDate);
    }
}
