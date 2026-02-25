package com.mtsaas.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditBalanceResponse {
    private Long availableCredits;
    private Long totalCreditsUsed;
    private Long totalCreditsPurchased;
    private String lastUpdated;
    
    // Monthly subscription tracking
    private boolean isMonthly;
    private String creditsExpiryDate;
    private Long daysUntilExpiry;
    private String subscriptionStatus; // "ACTIVE", "EXPIRED", "NOT_SUBSCRIBED"
}

