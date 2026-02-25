package com.mtsaas.backend.api;

import com.mtsaas.backend.domain.Conversion;
import com.mtsaas.backend.domain.User;
import com.mtsaas.backend.dto.AnalyticsResponse;
import com.mtsaas.backend.infrastructure.repository.ConversionRepository;
import com.mtsaas.backend.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final UserRepository userRepository;
    private final ConversionRepository conversionRepository;

    @GetMapping("/stats")
    public ResponseEntity<AnalyticsResponse> getAnalyticsStats(
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Fetching analytics stats for user: {}", userDetails.getUsername());

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<AnalyticsResponse.DailyCount> dailyCounts = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

            // Note: countByUserIdAndCreatedAtBetween might be better, but we have After
            // Let's quickly add countByUserIdAndCreatedAtBetween to repository or use a
            // simple loop
            long count = conversionRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                    .filter(c -> !c.getCreatedAt().isBefore(startOfDay) && c.getCreatedAt().isBefore(endOfDay))
                    .count();

            dailyCounts.add(AnalyticsResponse.DailyCount.builder()
                    .date(date)
                    .count(count)
                    .build());
        }

        long total = conversionRepository.countByUserEmail(user.getEmail());
        long successful = conversionRepository.countByUserIdAndStatus(user.getId(), Conversion.Status.SUCCESS);
        long failed = conversionRepository.countByUserIdAndStatus(user.getId(), Conversion.Status.FAILED);

        String rate = total == 0 ? "100%" : Math.round((double) successful / total * 100) + "%";

        AnalyticsResponse response = AnalyticsResponse.builder()
                .dailyCounts(dailyCounts)
                .summary(AnalyticsResponse.SuccessMetrics.builder()
                        .total(total)
                        .successful(successful)
                        .failed(failed)
                        .successRate(rate)
                        .build())
                .build();

        return ResponseEntity.ok(response);
    }
}
