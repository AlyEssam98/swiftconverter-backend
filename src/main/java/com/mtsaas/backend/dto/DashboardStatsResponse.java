package com.mtsaas.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class DashboardStatsResponse {
    private long availableCredits;
    private long conversionsToday;
    private String successRate;
    private long totalConversions;
    private List<RecentActivity> recentActivity;

    @Data
    @Builder
    public static class RecentActivity {
        private UUID id;
        private String type;
        private String status;
        private LocalDateTime timestamp;
    }
}
