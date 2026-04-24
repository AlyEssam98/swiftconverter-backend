package com.mtsaas.backend.api;

import com.mtsaas.backend.application.exception.InvalidPurchaseRequestException;
import com.mtsaas.backend.application.exception.PaymentConfigurationException;
import com.mtsaas.backend.application.exception.PaymentGatewayUnavailableException;
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
import java.util.HashMap;
import java.time.LocalDateTime;

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
        } catch (InvalidPurchaseRequestException e) {
            log.warn("Invalid purchase request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error", "Invalid purchase request",
                            "message", e.getMessage()));
        } catch (PaymentConfigurationException | PaymentGatewayUnavailableException e) {
            log.error("Payment gateway unavailable for purchase request", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "Payment system temporarily unavailable",
                            "message",
                            "We're currently unable to process payments. Please try again later or contact support."));
        } catch (RuntimeException e) {
            log.error("Unexpected purchase failure", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Purchase request failed",
                            "message", "Unexpected error while processing purchase request."));
        }
    }

    @GetMapping("/usage")
    public ResponseEntity<CreditUsageResponse> getCreditUsage(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Getting credit usage for user: {}", userDetails.getUsername());
        CreditUsageResponse response = creditService.getCreditUsage(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/debug/config")
    public ResponseEntity<Map<String, Object>> debugConfig() {
        Map<String, Object> debug = new HashMap<>();
        debug.put("timestamp", LocalDateTime.now().toString());
        debug.put("service_status", "Backend is running");
        debug.put("lemon_squeezy_configured", creditService.isLemonSqueezyConfigured());
        debug.put("missing_configs", creditService.getMissingLemonSqueezyConfigs());
        return ResponseEntity.ok(debug);
    }

    @GetMapping("/debug/lemon-test")
    public ResponseEntity<Map<String, Object>> testLemonSqueezy() {
        Map<String, Object> result = new HashMap<>();
        try {
            // Test API connectivity
            result.put("api_test", "Attempting to test Lemon Squeezy API...");
            // This will throw if configuration is missing
            creditService.testLemonSqueezyConnection();
            result.put("api_test", "SUCCESS - Lemon Squeezy API is accessible");
            result.put("status", "connected");
        } catch (Exception e) {
            result.put("api_test", "FAILED - " + e.getMessage());
            result.put("status", "failed");
            result.put("error_type", e.getClass().getSimpleName());
        }
        return ResponseEntity.ok(result);
    }
}
