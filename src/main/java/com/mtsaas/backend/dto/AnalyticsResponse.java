package com.mtsaas.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class AnalyticsResponse {
    private List<DailyCount> dailyCounts;
    private SuccessMetrics summary;

    @Data
    @Builder
    public static class DailyCount {
        private LocalDate date;
        private long count;
    }

    @Data
    @Builder
    public static class SuccessMetrics {
        private long total;
        private long successful;
        private long failed;
        private String successRate;
    }
}
