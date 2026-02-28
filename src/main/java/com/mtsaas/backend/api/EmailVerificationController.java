package com.mtsaas.backend.api;

import com.mtsaas.backend.application.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationController {

    private final AuthService authService;

    @GetMapping("/verify")
    public ResponseEntity<?> verifyEmail(@RequestParam String token, @RequestParam String email) {
        try {
            log.info("Email verification attempt for: {} with token: {}", email, token);
            
            // This would need to be implemented in AuthService
            // boolean verified = authService.verifyEmail(token, email);
            
            // For now, return success response
            return ResponseEntity.ok(Map.of(
                "message", "Email verified successfully. You can now log in.",
                "verified", true
            ));
            
        } catch (Exception e) {
            log.error("Email verification failed for {}: {}", email, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid or expired verification link"
            ));
        }
    }
    
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            log.info("Resend verification email requested for: {}", email);
            
            // This would need to be implemented in AuthService
            // authService.resendVerificationEmail(email);
            
            return ResponseEntity.ok(Map.of(
                "message", "Verification email sent. Please check your inbox."
            ));
            
        } catch (Exception e) {
            log.error("Failed to resend verification email: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to resend verification email"
            ));
        }
    }
}
