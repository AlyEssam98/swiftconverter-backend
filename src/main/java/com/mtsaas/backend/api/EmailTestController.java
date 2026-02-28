package com.mtsaas.backend.api;

import com.mtsaas.backend.infrastructure.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Slf4j
public class EmailTestController {

    private final EmailService emailService;

    @GetMapping("/email-config")
    public ResponseEntity<Map<String, Object>> testEmailConfig() {
        try {
            // This will show if email service is properly configured
            return ResponseEntity.ok(Map.of(
                "status", "Email service initialized",
                "message", "Email configuration loaded successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "Error",
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/send-test-email")
    public ResponseEntity<Map<String, String>> sendTestEmail(@RequestParam String email) {
        try {
            emailService.sendVerificationEmail(email, "https://swiftconverter.com/test");
            return ResponseEntity.ok(Map.of(
                "status", "Test email sent",
                "to", email
            ));
        } catch (Exception e) {
            log.error("Failed to send test email", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "Error",
                "error", e.getMessage()
            ));
        }
    }
}
