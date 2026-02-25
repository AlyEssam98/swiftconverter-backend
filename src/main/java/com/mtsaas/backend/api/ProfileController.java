package com.mtsaas.backend.api;

import com.mtsaas.backend.domain.User;
import com.mtsaas.backend.dto.ProfileResponse;
import com.mtsaas.backend.dto.ProfileUpdateRequest;
import com.mtsaas.backend.dto.CreditBalanceResponse;
import com.mtsaas.backend.application.service.CreditService;
import com.mtsaas.backend.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final UserRepository userRepository;
    private final CreditService creditService;

    private ProfileResponse buildProfileResponse(User user) {
        // Get the actual credit balance from CreditPurchase records, not User.credits
        CreditBalanceResponse creditBalance = creditService.getUserCreditBalance(user.getEmail());
        
        return ProfileResponse.builder()
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .credits(creditBalance.getAvailableCredits())  // Use calculated credits from CreditPurchase records
                .isMonthly(creditBalance.isMonthly())
                .creditsExpiryDate(creditBalance.getCreditsExpiryDate())
                .daysUntilExpiry(creditBalance.getDaysUntilExpiry())
                .build();
    }

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Fetching profile for user: {}", userDetails.getUsername());
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(buildProfileResponse(user));
    }

    @PutMapping
    public ResponseEntity<ProfileResponse> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ProfileUpdateRequest request) {
        log.info("Updating profile for user: {}", userDetails.getUsername());
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setDisplayName(request.getDisplayName());
        userRepository.save(user);

        return ResponseEntity.ok(buildProfileResponse(user));
    }
}
