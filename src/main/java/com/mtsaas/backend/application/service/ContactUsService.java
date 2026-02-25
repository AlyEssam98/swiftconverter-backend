package com.mtsaas.backend.application.service;

import com.mtsaas.backend.application.dto.ContactUsDto;
import com.mtsaas.backend.domain.ContactUs;
import com.mtsaas.backend.domain.User;
import com.mtsaas.backend.infrastructure.repository.ContactUsRepository;
import com.mtsaas.backend.infrastructure.repository.UserRepository;
import com.mtsaas.backend.infrastructure.email.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactUsService {

    private final ContactUsRepository contactUsRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public ContactUsDto.ContactUsResponse submitContactUs(String email, ContactUsDto.ContactUsRequest request, HttpServletRequest httpRequest) {
        log.info("Processing Contact Us request - Email: {}, AuthenticatedUser: {}", request.getEmail(), email);
        
        try {
            ContactUs.ContactUsBuilder builder = ContactUs.builder()
                    .message(request.getMessage())
                    .status(ContactUs.ContactUsStatus.NEW);

            // Get user email and name from form (prioritize form data)
            String userEmail = request.getEmail() != null ? request.getEmail().trim() : email;
            String userName = request.getName() != null ? request.getName().trim() : "User";
            String subject = request.getSubject() != null ? request.getSubject().trim() : "Contact Form Submission";

            log.info("Contact Us details - UserName: {}, UserEmail: {}, Subject: {}", userName, userEmail, subject);

            builder.userEmail(userEmail != null ? userEmail : "anonymous@contact")
                   .userName(userName)
                   .subject(subject);

            // Link to authenticated user if available
            if (email != null) {
                User user = userRepository.findByEmail(email).orElse(null);
                if (user != null) {
                    builder.user(user);
                    log.info("Linked Contact Us to authenticated user: {}", email);
                }
            }

            // Capture IP and user agent
            String ipAddress = getClientIp(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");

            builder.ipAddress(ipAddress);
            builder.userAgent(userAgent);

            ContactUs contactUs = contactUsRepository.save(builder.build());
            log.info("Contact Us saved to database with ID: {}", contactUs.getId());

            // Send email notification to admin only if we have a valid user email
            if (userEmail != null && !userEmail.equals("anonymous@contact")) {
                try {
                    emailService.sendContactUsNotification(
                            userName,
                            userEmail,
                            subject,
                            request.getMessage()
                    );
                    log.info("✓ Contact Us email notification sent from: {}", userEmail);
                } catch (Exception e) {
                    log.warn("Failed to send contact us email notification from {}: {}", userEmail, e.getMessage());
                    // Don't fail the request if email fails - response still succeeds
                }
            } else {
                log.info("Contact Us message from anonymous user stored in database (no email to contact back)");
            }

            return mapToResponse(contactUs);
        } catch (Exception e) {
            log.error("Error processing Contact Us request: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process contact us request: " + e.getMessage(), e);
        }
    }

    public List<ContactUsDto.ContactUsResponse> getUserContactUs(String email) {
        return contactUsRepository.findByUserEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<ContactUsDto.ContactUsResponse> getAllContactUs() {
        return contactUsRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private ContactUsDto.ContactUsResponse mapToResponse(ContactUs contactUs) {
        return ContactUsDto.ContactUsResponse.builder()
                .id(contactUs.getId() != null ? contactUs.getId().toString() : null)
                .name(contactUs.getUserName())
                .email(contactUs.getUserEmail())
                .subject(contactUs.getSubject())
                .message(contactUs.getMessage())
                .status(contactUs.getStatus() != null ? contactUs.getStatus().name() : "NEW")
                .createdAt(contactUs.getCreatedAt() != null ? contactUs.getCreatedAt().toString() : null)
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}
