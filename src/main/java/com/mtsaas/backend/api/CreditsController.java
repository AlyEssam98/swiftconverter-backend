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

    @PostMapping("/purchase/verify")
    public ResponseEntity<?> verifyPurchase(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> request) {
        String checkoutId = request.get("checkoutId");
        if (checkoutId == null || checkoutId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "checkoutId is required"));
        }
        try {
            log.info("Verifying purchase for user: {}, checkoutId: {}", userDetails.getUsername(), checkoutId);
            CreditBalanceResponse balance = creditService.verifyAndFulfillPurchase(
                    userDetails.getUsername(), checkoutId);
            return ResponseEntity.ok(balance);
        } catch (RuntimeException e) {
            log.error("Purchase verification failed for user: {}, checkoutId: {}",
                    userDetails.getUsername(), checkoutId, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of(
                            "error", "Verification failed",
                            "message", e.getMessage()));
        }
    }
}
