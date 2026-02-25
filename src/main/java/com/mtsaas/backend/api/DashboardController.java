package com.mtsaas.backend.api;

import com.mtsaas.backend.domain.Conversion;
import com.mtsaas.backend.domain.User;
import com.mtsaas.backend.dto.DashboardStatsResponse;
import com.mtsaas.backend.dto.CreditBalanceResponse;
import com.mtsaas.backend.application.service.CreditService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final UserRepository userRepository;
    private final ConversionRepository conversionRepository;
    private final CreditService creditService;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats(
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("Fetching dashboard stats for user: {}", userDetails.getUsername());

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        long totalConversions = conversionRepository.countByUserEmail(user.getEmail());
        long conversionsToday = conversionRepository.countByUserIdAndCreatedAtAfter(
                user.getId(), LocalDateTime.now().withHour(0).withMinute(0).withSecond(0));

        long successfulConversions = conversionRepository.countByUserIdAndStatus(
                user.getId(), Conversion.Status.SUCCESS);

        String successRate = totalConversions == 0 ? "100%"
                : Math.round((double) successfulConversions / totalConversions * 100) + "%";

        List<DashboardStatsResponse.RecentActivity> recentActivity = conversionRepository
                .findTop5ByUserEmailOrderByCreatedAtDesc(user.getEmail()).stream()
                .map(c -> DashboardStatsResponse.RecentActivity.builder()
                        .id(c.getId())
                        .type(c.getConversionType())
                        .status(c.getStatus().name())
                        .timestamp(c.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        // Get the actual credit balance from CreditPurchase records, not User.credits
        CreditBalanceResponse creditBalance = creditService.getUserCreditBalance(user.getEmail());
        
        DashboardStatsResponse response = DashboardStatsResponse.builder()
                .availableCredits(creditBalance.getAvailableCredits())  // Use calculated credits from CreditPurchase records
                .conversionsToday(conversionsToday)
                .successRate(successRate)
                .totalConversions(totalConversions)
                .recentActivity(recentActivity)
                .build();

        log.info("✓ Dashboard stats for user {}: {} credits available", user.getEmail(), creditBalance.getAvailableCredits());
        return ResponseEntity.ok(response);
    }
}
