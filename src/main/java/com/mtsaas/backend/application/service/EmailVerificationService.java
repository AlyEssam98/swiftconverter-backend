package com.mtsaas.backend.application.service;

import com.mtsaas.backend.infrastructure.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final EmailService emailService;

    public String generateVerificationToken() {
        return UUID.randomUUID().toString();
    }

    public void sendVerificationEmail(String userEmail, String verificationToken) {
        String verificationUrl = String.format(
            "https://swiftmtmxconverter.vercel.app/auth/verify?token=%s&email=%s",
            verificationToken,
            userEmail
        );

        try {
            log.info("Sending verification email to {} with token {}", userEmail, verificationToken);
            log.info("Verification URL: {}", verificationUrl);
            
            // Send actual verification email
            emailService.sendVerificationEmail(userEmail, verificationUrl);
            
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", userEmail, e.getMessage());
        }
    }
}
