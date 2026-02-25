package com.mtsaas.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CreditUsageResponse {
    
    private Long totalCreditsUsed;
    private Long creditsUsedThisMonth;
    private Long creditsUsedToday;
    private List<UsageRecord> recentUsage;
    
    @Data
    @Builder
    public static class UsageRecord {
        private UUID id;
        private Long creditsUsed;
        private String serviceType;
        private String description;
        private LocalDateTime createdAt;
    }
}
