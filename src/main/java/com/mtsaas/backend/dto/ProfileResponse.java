package com.mtsaas.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProfileResponse {
    private String email;
    private String displayName;
    private long credits;
    
    // Monthly subscription info
    private boolean isMonthly;
    private String creditsExpiryDate;
    private Long daysUntilExpiry;
}
