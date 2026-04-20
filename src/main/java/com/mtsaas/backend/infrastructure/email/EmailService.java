package com.mtsaas.backend.infrastructure.email;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Async;
import java.io.IOException;

@Service
@Lazy
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final SendGrid sendGrid;
    private final FallbackEmailService fallbackEmailService;

    @Value("${sendgrid.from.email:converterswift@gmail.com}")
    private String senderEmail;

    @Value("${app.support.email:converterswift@gmail.com}")
    private String supportEmail;

    @PostConstruct
    public void init() {
        log.info("✓ EmailService initialized using SendGrid API - Sender Email: {}, Support Email: {}", 
                senderEmail, supportEmail);
        
        // Validate configuration
        if (senderEmail == null || senderEmail.isEmpty()) {
            log.warn("⚠ sendgrid.from.email is not configured (SENDGRID_FROM_EMAIL in .env)");
        }
        if (supportEmail == null || supportEmail.isEmpty()) {
            log.warn("⚠ SUPPORT_EMAIL is not configured");
        }
        log.info("Email configuration loaded from properties for SendGrid");
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        Email from = new Email(senderEmail);
        Email recipient = new Email(to);
        Content content = new Content("text/html", htmlContent);
        Mail mail = new Mail(from, subject, recipient, content);

        Request request = new Request();
        try {
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sendGrid.api(request);
            
            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("✓ Email sent successfully to {}. Status: {}", to, response.getStatusCode());
            } else {
                log.error("❌ Failed to send email to {}. Status: {}, Body: {}", to, response.getStatusCode(), response.getBody());
                throw new RuntimeException("SendGrid API error: " + response.getStatusCode());
            }
        } catch (IOException ex) {
            log.error("❌ IOException sending email via SendGrid", ex);
            throw new RuntimeException("SendGrid IO error", ex);
        }
    }

    /**
     * Send feedback email notification with error handling
     */
    @Async
    public void sendFeedbackNotification(String userEmail, String message) {
        try {
            log.debug("Sending feedback email from {} to support", userEmail);
            
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

            sendEmail(supportEmail, "New Feedback from SWIFT Converter: " + userEmail, htmlContent);

        } catch (Exception e) {
            log.error("❌ Unexpected error sending feedback email", e);
            fallbackEmailService.logFeedbackEmail(userEmail, message);
        }
    }

    /**
     * Send contact us email notification with error handling
     */
    @Async
    public void sendContactUsNotification(String userName, String userEmail, String subject, String message) {
        try {
            log.debug("Sending contact us email from {} to support", userEmail);

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

            sendEmail(supportEmail, "New Contact Us Message: " + subject, htmlContent);

        } catch (Exception e) {
            log.error("❌ Unexpected error sending contact us email", e);
            fallbackEmailService.logContactUsEmail(userName, userEmail, subject, message);
        }
    }

    /**
     * Send credit purchase confirmation email
     */
    @Async
    public void sendCreditPurchaseConfirmation(String userEmail, long creditsAmount, String packageName, java.math.BigDecimal amount) {
        try {
            log.debug("Sending purchase confirmation email to support for user {}", userEmail);

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

            sendEmail(supportEmail, "Purchase Confirmation - " + userEmail, htmlContent);

        } catch (Exception e) {
            log.error("❌ Unexpected error sending purchase confirmation email", e);
        }
    }

    /**
     * Send credit purchase notification to admin
     */
    @Async
    public void sendAdminPurchaseNotification(String userEmail, long creditsAmount, String packageName, java.math.BigDecimal amount) {
        try {
            log.debug("Sending admin purchase notification to support for user {}", userEmail);

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

            sendEmail(supportEmail, "[ALERT] Purchase Completed - " + userEmail, htmlContent);

        } catch (Exception e) {
            log.error("❌ Unexpected error sending admin notification email", e);
        }
    }

    /**
     * Send email verification email
     */
    @Async
    public void sendVerificationEmail(String userEmail, String verificationUrl) {
        try {
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
                "                This link will expire in 30 minutes. If you didn't create an account, please ignore this email." +
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

            sendEmail(userEmail, "Verify your SWIFT Converter Pro account", htmlContent);

        } catch (Exception e) {
            log.error("❌ Unexpected error sending verification email", e);
            fallbackEmailService.logVerificationEmail(userEmail, verificationUrl);
        }
    }

    /**
     * Diagnostic method to test SendGrid connectivity
     */
    public String testEmailConnectivity() {
        try {
            log.info("Testing SendGrid connectivity...");
            Request request = new Request();
            request.setMethod(Method.GET);
            request.setEndpoint("user/profile");
            Response response = sendGrid.api(request);
            
            if (response.getStatusCode() == 200) {
                return "Connection successful to SendGrid API. Status: " + response.getStatusCode();
            } else {
                return "SendGrid API returned status: " + response.getStatusCode() + ". Body: " + response.getBody();
            }
        } catch (Exception e) {
            log.error("SendGrid Test failed");
            log.error("Exception Details: ", e);
            return "SendGrid Test failed: " + e.getMessage();
        }
    }
}
