package com.mtsaas.backend.api;

import com.mtsaas.backend.application.service.CreditService;
import com.mtsaas.backend.dto.CreditBalanceResponse;
import com.mtsaas.backend.dto.CreditPackageResponse;
import com.mtsaas.backend.dto.PurchaseCreditsRequest;
import com.mtsaas.backend.dto.PurchaseCreditsResponse;
import com.mtsaas.backend.dto.CreditUsageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/credits")
@RequiredArgsConstructor
@Slf4j
public class CreditsController {

    private final CreditService creditService;

    @GetMapping("/balance")
    public ResponseEntity<CreditBalanceResponse> getCreditBalance(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Getting credit balance for user: {}", userDetails.getUsername());
        CreditBalanceResponse response = creditService.getUserCreditBalance(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/packages")
    public ResponseEntity<List<CreditPackageResponse>> getCreditPackages() {
        log.info("Getting available credit packages");
        List<CreditPackageResponse> packages = creditService.getAvailablePackages();
        return ResponseEntity.ok(packages);
    }

    @PostMapping("/purchase")
    public ResponseEntity<?> purchaseCredits(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody PurchaseCreditsRequest request) {
        try {
            log.info("Purchasing credits for user: {}, package: {}",
                    userDetails.getUsername(), request.getPackageId());
            PurchaseCreditsResponse response = creditService.purchaseCredits(
                    userDetails.getUsername(), request.getPackageId());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Purchase failed: {}", e.getMessage());

            // Handle payment system not configured
            if (e.getMessage().contains("Payment system is not configured") ||
                    e.getMessage().contains("Payment gateway initialization failed")) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of(
                                "error", "Payment system temporarily unavailable",
                                "message",
                                "We're currently unable to process payments. Please try again later or contact support."));
            }

            // Handle other errors
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/usage")
    public ResponseEntity<CreditUsageResponse> getCreditUsage(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Getting credit usage for user: {}", userDetails.getUsername());
        CreditUsageResponse response = creditService.getCreditUsage(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }
}
