package com.mtsaas.backend.api;

import com.mtsaas.backend.application.dto.AuthDto;
import com.mtsaas.backend.application.service.AuthService;
import com.mtsaas.backend.infrastructure.email.EmailService;
import com.mtsaas.backend.infrastructure.security.JwtService;
import com.mtsaas.backend.infrastructure.security.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final EmailService emailService;

    @PostMapping("/register")
    public ResponseEntity<AuthDto.AuthenticationResponse> register(
            @RequestBody AuthDto.RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthDto.AuthenticationResponse> authenticate(
            @RequestBody AuthDto.AuthenticationRequest request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // Get token expiration and blacklist it
            long expirationTime = jwtService.extractExpirationTime(token);
            tokenBlacklistService.blacklistToken(token, expirationTime);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthDto.AuthenticationResponse> verifyEmail(
            @RequestBody AuthDto.VerifyEmailRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request.getToken()));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(
            @RequestBody AuthDto.ResendVerificationRequest request) {
        authService.resendVerification(request.getEmail());
        return ResponseEntity.ok().build();
    }

    // Diagnostic endpoints for email testing
    @GetMapping("/test-email")
    public ResponseEntity<Map<String, String>> testEmail() {
        String result = emailService.testEmailConnectivity();
        return ResponseEntity.ok(Map.of("result", result));
    }

    @GetMapping("/email-config")
    public ResponseEntity<Map<String, Object>> testEmailConfig() {
        return ResponseEntity.ok(Map.of(
            "status", "Email service initialized",
            "message", "Email configuration loaded successfully"
        ));
    }

    @GetMapping("/send-test-email")
    public ResponseEntity<Map<String, String>> sendTestEmail(@RequestParam String email) {
        try {
            emailService.sendVerificationEmail(email, "https://swiftconverter.com/test");
            return ResponseEntity.ok(Map.of(
                "status", "Test email sent",
                "to", email
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "Error",
                "error", e.getMessage()
            ));
        }
    }
}
