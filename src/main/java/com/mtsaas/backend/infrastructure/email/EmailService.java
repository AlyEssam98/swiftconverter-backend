package com.mtsaas.backend.infrastructure.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final FallbackEmailService fallbackEmailService;

    @Value("${spring.mail.username:converterswift@gmail.com}")
    private String senderEmail;

    @Value("${app.support.email:converterswift@gmail.com}")
    private String supportEmail;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @PostConstruct
    public void init() {
        log.info("✓ EmailService initialized - Mail Host: {}, Mail Port: {}, Sender Email: {}, Support Email: {}", 
                mailHost, mailPort, senderEmail, supportEmail);
        
        // Validate configuration
        if (mailHost == null || mailHost.isEmpty()) {
            log.warn("⚠ MAIL_HOST is not configured");
        }
        if (senderEmail == null || senderEmail.isEmpty()) {
            log.warn("⚠ spring.mail.username is not configured (MAIL_USERNAME in .env)");
        }
        if (supportEmail == null || supportEmail.isEmpty()) {
            log.warn("⚠ SUPPORT_EMAIL is not configured");
        }
        log.info("Email configuration loaded from properties");
    }

    /**
     * Send feedback email notification with error handling
     */
    public void sendFeedbackNotification(String userEmail, String message) {
        try {
            log.debug("Sending feedback email from {} to support", userEmail);
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(supportEmail);  // FROM: support email
            helper.setTo(supportEmail);    // TO: support email
            helper.setSubject("New Feedback from SWIFT Converter: " + userEmail);

            String htmlContent = String.format(
                    "<html>" +
                    "<body style='font-family: Arial, sans-serif;'>" +
                    "<h2 style='color: #333;'>New Feedback Received</h2>" +
                    "<p><strong>From Customer:</strong> %s</p>" +
                    "<p><strong>Message:</strong></p>" +
                    "<p style='background-color: #f5f5f5; padding: 15px; border-left: 4px solid #007bff;'>%s</p>" +
                    "<p style='color: #666; font-size: 12px; margin-top: 20px;'>" +
                    "This is an automated notification from SWIFT Converter." +
                    "</p>" +
                    "</body>" +
                    "</html>",
                    userEmail,
                    message.replace("\n", "<br/>")
            );

            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);

            log.info("✓ Feedback email sent successfully - From: {} To: {}", supportEmail, supportEmail);
        } catch (MailAuthenticationException e) {
            log.error("❌ Email authentication failed - Gmail authentication error");
            log.error("   - Check MAIL_USERNAME and MAIL_PASSWORD in .env file");
            log.error("   - MAIL_PASSWORD should be a Gmail App Password (16 characters), not your regular password");
            log.error("   - Ensure 2-factor authentication is enabled on Gmail account");
            log.error("   - Details: {}", e.getMessage());
            logEmailConfiguration();
            fallbackEmailService.logFeedbackEmail(userEmail, message);
        } catch (MessagingException e) {
            log.error("❌ Failed to send feedback email: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            fallbackEmailService.logFeedbackEmail(userEmail, message);
        } catch (Exception e) {
            log.error("❌ Unexpected error sending feedback email", e);
            fallbackEmailService.logFeedbackEmail(userEmail, message);
        }
    }

    /**
     * Send contact us email notification with error handling
     */
    public void sendContactUsNotification(String userName, String userEmail, String subject, String message) {
        try {
            log.debug("Sending contact us email from {} to support", userEmail);
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(supportEmail);  // FROM: support email
            helper.setTo(supportEmail);    // TO: support email
            helper.setSubject("New Contact Us Message: " + subject);

            String htmlContent = String.format(
                    "<html>" +
                    "<body style='font-family: Arial, sans-serif;'>" +
                    "<h2 style='color: #333;'>New Contact Us Message</h2>" +
                    "<p><strong>From:</strong> %s (%s)</p>" +
                    "<p><strong>Subject:</strong> %s</p>" +
                    "<p><strong>Message:</strong></p>" +
                    "<p style='background-color: #f5f5f5; padding: 15px; border-left: 4px solid #007bff;'>%s</p>" +
                    "<hr style='margin: 20px 0; border: none; border-top: 1px solid #ddd;'>" +
                    "<p style='color: #666; font-size: 12px;'>" +
                    "This is an automated notification from SWIFT Converter. Reply to: %s" +
                    "</p>" +
                    "</body>" +
                    "</html>",
                    userName,
                    userEmail,
                    subject,
                    message.replace("\n", "<br/>"),
                    userEmail
            );

            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);

            log.info("✓ Contact Us email sent successfully - From: {} To: {} ReplyTo: {}", supportEmail, supportEmail, userEmail);
        } catch (MailAuthenticationException e) {
            log.error("❌ Email authentication failed - Gmail authentication error");
            log.error("   - Check MAIL_USERNAME and MAIL_PASSWORD in .env file");
            log.error("   - MAIL_PASSWORD should be a Gmail App Password (16 characters), not your regular password");
            log.error("   - Ensure 2-factor authentication is enabled on Gmail account");
            log.error("   - Details: {}", e.getMessage());
            logEmailConfiguration();
            fallbackEmailService.logContactUsEmail(userName, userEmail, subject, message);
        } catch (MessagingException e) {
            log.error("❌ Failed to send contact us email: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            fallbackEmailService.logContactUsEmail(userName, userEmail, subject, message);
        } catch (Exception e) {
            log.error("❌ Unexpected error sending contact us email", e);
            fallbackEmailService.logContactUsEmail(userName, userEmail, subject, message);
        }
    }

    /**
     * Send credit purchase confirmation email
     */
    public void sendCreditPurchaseConfirmation(String userEmail, long creditsAmount, String packageName, java.math.BigDecimal amount) {
        try {
            log.debug("Sending purchase confirmation email to support for user {}", userEmail);
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(supportEmail);      // FROM: support email
            helper.setTo(supportEmail);        // TO: support email
            helper.setSubject("Purchase Confirmation - " + userEmail);

            String htmlContent = String.format(
                    "<html>" +
                    "<body style='font-family: Arial, sans-serif;'>" +
                    "<div style='max-width: 600px; margin: 0 auto;'>" +
                    "<h2 style='color: #007bff;'>✓ Credit Purchase Confirmed</h2>" +
                    "<p><strong>Customer Email:</strong> %s</p>" +
                    "<div style='background-color: #f0f8ff; padding: 20px; border-radius: 8px; margin: 20px 0;'>" +
                    "<h3 style='margin: 0 0 15px 0;'>Purchase Summary</h3>" +
                    "<table style='width: 100%%; border-collapse: collapse;'>" +
                    "<tr style='border-bottom: 1px solid #ddd;'>" +
                    "<td style='padding: 10px 0;'><strong>Package:</strong></td>" +
                    "<td style='padding: 10px 0; text-align: right;'>%s</td>" +
                    "</tr>" +
                    "<tr style='border-bottom: 1px solid #ddd;'>" +
                    "<td style='padding: 10px 0;'><strong>Credits:</strong></td>" +
                    "<td style='padding: 10px 0; text-align: right;'>%d conversions</td>" +
                    "</tr>" +
                    "<tr>" +
                    "<td style='padding: 10px 0;'><strong>Amount Paid:</strong></td>" +
                    "<td style='padding: 10px 0; text-align: right; font-size: 16px; color: #28a745;'><strong>$%s</strong></td>" +
                    "</tr>" +
                    "</table>" +
                    "</div>" +
                    "<p><strong>Valid for 30 days from purchase date.</strong></p>" +
                    "<p>Credits have been added to customer account.</p>" +
                    "<hr style='margin: 30px 0; border: none; border-top: 1px solid #ddd;'>" +
                    "<p style='color: #666; font-size: 12px;'>Automated notification from SWIFT Converter system</p>" +
                    "</div>" +
                    "</body>" +
                    "</html>",
                    userEmail, packageName, creditsAmount, amount
            );

            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);

            log.info("✓ Purchase confirmation email sent to support for user {} ({} credits)", userEmail, creditsAmount);
        } catch (MailAuthenticationException e) {
            log.error("❌ Email authentication failed - Check MAIL_PASSWORD in .env. Details: {}", e.getMessage());
            logEmailConfiguration();
        } catch (MessagingException e) {
            log.error("❌ Failed to send purchase confirmation email: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        } catch (Exception e) {
            log.error("❌ Unexpected error sending purchase confirmation email", e);
        }
    }

    /**
     * Send credit purchase notification to admin
     */
    public void sendAdminPurchaseNotification(String userEmail, long creditsAmount, String packageName, java.math.BigDecimal amount) {
        try {
            log.debug("Sending admin purchase notification to support for user {}", userEmail);
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(supportEmail);     // FROM: support email
            helper.setTo(supportEmail);       // TO: support email
            helper.setSubject("[ALERT] Purchase Completed - " + userEmail);

            String htmlContent = String.format(
                    "<html>" +
                    "<body style='font-family: Arial, sans-serif;'>" +
                    "<h2 style='color: #28a745;'>✓ Purchase Successfully Processed</h2>" +
                    "<table style='width: 100%%; border-collapse: collapse; margin: 20px 0;'>" +
                    "<tr style='background-color: #f5f5f5;'>" +
                    "<td style='padding: 10px; border: 1px solid #ddd;'><strong>Customer Email</strong></td>" +
                    "<td style='padding: 10px; border: 1px solid #ddd;'>%s</td>" +
                    "</tr>" +
                    "<tr>" +
                    "<td style='padding: 10px; border: 1px solid #ddd;'><strong>Package</strong></td>" +
                    "<td style='padding: 10px; border: 1px solid #ddd;'>%s</td>" +
                    "</tr>" +
                    "<tr style='background-color: #f5f5f5;'>" +
                    "<td style='padding: 10px; border: 1px solid #ddd;'><strong>Credits Purchased</strong></td>" +
                    "<td style='padding: 10px; border: 1px solid #ddd;'>%d</td>" +
                    "</tr>" +
                    "<tr>" +
                    "<td style='padding: 10px; border: 1px solid #ddd;'><strong>Amount</strong></td>" +
                    "<td style='padding: 10px; border: 1px solid #ddd; color: #28a745;'><strong>$%s</strong></td>" +
                    "</tr>" +
                    "<tr style='background-color: #f5f5f5;'>" +
                    "<td style='padding: 10px; border: 1px solid #ddd;'><strong>Timestamp</strong></td>" +
                    "<td style='padding: 10px; border: 1px solid #ddd;'>%s</td>" +
                    "</tr>" +
                    "</table>" +
                    "<p style='color: #666; font-size: 12px;'>Customer can now use their credits. Credits expire 30 days from purchase.</p>" +
                    "</body>" +
                    "</html>",
                    userEmail, packageName, creditsAmount, amount,
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );

            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);

            log.info("✓ Admin purchase notification sent to support for {}", userEmail);
        } catch (MailAuthenticationException e) {
            log.error("❌ Email authentication failed - Check MAIL_PASSWORD in .env. Details: {}", e.getMessage());
            logEmailConfiguration();
        } catch (MessagingException e) {
            log.error("❌ Failed to send admin notification email: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        } catch (Exception e) {
            log.error("❌ Unexpected error sending admin notification email", e);
        }
    }

    /**
     * Send email verification email
     */
    public void sendVerificationEmail(String userEmail, String verificationUrl) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(userEmail);
            helper.setSubject("Verify your SWIFT Converter Pro account");

            // Hide sender from reply-to
            helper.setReplyTo(supportEmail);

            String htmlContent = String.format(
                "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta charset='UTF-8'>" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "    <title>Verify Your SWIFT Converter Pro Account</title>" +
                "    <style>" +
                "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: #333; }" +
                "        .container { max-width: 600px; margin: 0 auto; padding: 20px; }" +
                "        .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 30px; text-align: center; border-radius: 8px; }" +
                "        .header h1 { color: white; margin: 0; font-size: 28px; }" +
                "        .content { background: #f9f9f9; padding: 30px; border-radius: 8px; margin: 20px 0; }" +
                "        .button { display: inline-block; background: #4CAF50; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; margin: 20px 0; }" +
                "        .footer { text-align: center; color: #666; font-size: 14px; margin-top: 30px; }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div class='container'>" +
                "        <div class='header'>" +
                "            <h1>SWIFT Converter Pro</h1>" +
                "        </div>" +
                "        <div class='content'>" +
                "            <h2>Welcome to SWIFT Converter Pro!</h2>" +
                "            <p>Thank you for signing up. To complete your registration and start using our MT to MX conversion tools, please verify your email address.</p>" +
                "            <p><strong>Why verify?</strong></p>" +
                "            <ul>" +
                "                <li>✅ Get 5 free credits to start</li>" +
                "                <li>✅ Access MT103, MT202, MT940 conversion</li>" +
                "                <li>✅ Generate ISO 20022 compliant output</li>" +
                "            </ul>" +
                "            <p style='text-align: center;'>" +
                "                <a href='%s' class='button'>Verify Your Email</a>" +
                "            </p>" +
                "            <p style='font-size: 14px; color: #666;'>" +
                "                Or copy and paste this link into your browser:<br>" +
                "                <span style='word-break: break-all;'>%s</span>" +
                "            </p>" +
                "            <p style='font-size: 12px; color: #999;'>" +
                "                This link will expire in 24 hours. If you didn't create an account, please ignore this email." +
                "            </p>" +
                "        </div>" +
                "        <div class='footer'>" +
                "            <p>Best regards,<br>The SWIFT Converter Pro Team</p>" +
                "            <p style='font-size: 11px; color: #999;'>" +
                "                This is an automated message. Please do not reply to this email." +
                "            </p>" +
                "        </div>" +
                "    </div>" +
                "</body>" +
                "</html>",
                verificationUrl, verificationUrl
            );

            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);

            log.info("✓ Verification email sent to {}", userEmail);
        } catch (MailAuthenticationException e) {
            log.error("❌ Email authentication failed - Check MAIL_PASSWORD in .env. Details: {}", e.getMessage());
            logEmailConfiguration();
            fallbackEmailService.logVerificationEmail(userEmail, verificationUrl);
        } catch (MessagingException e) {
            log.error("❌ Failed to send verification email: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            fallbackEmailService.logVerificationEmail(userEmail, verificationUrl);
        } catch (Exception e) {
            log.error("❌ Unexpected error sending verification email", e);
            fallbackEmailService.logVerificationEmail(userEmail, verificationUrl);
        }
    }

    /**
     * Log email configuration for debugging
     */
    private void logEmailConfiguration() {
        String maskedEmail = senderEmail != null && senderEmail.length() > 3 
            ? senderEmail.substring(0, 3) + "***" 
            : "***";
        log.info("Email Configuration: host={}, port={}, username={}", mailHost, mailPort, maskedEmail);
    }
}
