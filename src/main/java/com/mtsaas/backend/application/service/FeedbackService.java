package com.mtsaas.backend.application.service;

import com.mtsaas.backend.application.dto.FeedbackDto;
import com.mtsaas.backend.domain.Feedback;
import com.mtsaas.backend.domain.User;
import com.mtsaas.backend.infrastructure.repository.FeedbackRepository;
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
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Transactional
    public FeedbackDto.FeedbackResponse submitFeedback(String email, FeedbackDto.FeedbackRequest request, HttpServletRequest httpRequest) {
        Feedback.FeedbackBuilder builder = Feedback.builder()
                .message(request.getMessage());

        if (email != null) {
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                builder.user(user);
                builder.userEmail(email);
            }
        }

        // Capture IP and user agent for anonymous feedback
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        builder.ipAddress(ipAddress);
        builder.userAgent(userAgent);

        Feedback feedback = feedbackRepository.save(builder.build());

        // Send email notification to admin
        if (email != null) {
            try {
                emailService.sendFeedbackNotification(email, request.getMessage());
                log.info("Feedback email notification sent for user: {}", email);
            } catch (Exception e) {
                // Log but don't fail the request if email fails
                log.error("Failed to send feedback email notification for user {}: {}", email, e.getMessage(), e);
            }
        }

        return mapToResponse(feedback);
    }

    public List<FeedbackDto.FeedbackResponse> getUserFeedback(String email) {
        return feedbackRepository.findByUserEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<FeedbackDto.FeedbackResponse> getAllFeedback() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private FeedbackDto.FeedbackResponse mapToResponse(Feedback feedback) {
        return FeedbackDto.FeedbackResponse.builder()
                .id(feedback.getId().toString())
                .message(feedback.getMessage())
                .userEmail(feedback.getUserEmail())
                .status(feedback.getStatus().name())
                .createdAt(feedback.getCreatedAt() != null ? feedback.getCreatedAt().toString() : null)
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
