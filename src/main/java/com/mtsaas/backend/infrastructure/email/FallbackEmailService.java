package com.mtsaas.backend.infrastructure.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FallbackEmailService {

    public void logVerificationEmail(String userEmail, String verificationUrl) {
        log.info("📧 EMAIL VERIFICATION - Would send to: {}", userEmail);
        log.info("🔗 VERIFICATION URL: {}", verificationUrl);
        log.info("⚠️  Email service unavailable - please configure MAIL_USERNAME and MAIL_PASSWORD in Railway");
    }

    public void logContactUsEmail(String userName, String userEmail, String subject, String message) {
        log.info("📧 CONTACT US - From: {} <{}>", userName, userEmail);
        log.info("📝 Subject: {}", subject);
        log.info("💬 Message: {}", message);
        log.info("⚠️  Email service unavailable - please configure MAIL_USERNAME and MAIL_PASSWORD in Railway");
    }

    public void logFeedbackEmail(String userEmail, String message) {
        log.info("📧 FEEDBACK - From: {}", userEmail);
        log.info("💬 Message: {}", message);
        log.info("⚠️  Email service unavailable - please configure MAIL_USERNAME and MAIL_PASSWORD in Railway");
    }
}
