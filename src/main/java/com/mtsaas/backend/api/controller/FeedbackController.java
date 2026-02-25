package com.mtsaas.backend.api.controller;

import com.mtsaas.backend.application.dto.FeedbackDto;
import com.mtsaas.backend.application.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<FeedbackDto.FeedbackResponse> submitFeedback(
            @Valid @RequestBody FeedbackDto.FeedbackRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        String email = userDetails != null ? userDetails.getUsername() : null;
        FeedbackDto.FeedbackResponse response = feedbackService.submitFeedback(email, request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    public ResponseEntity<List<FeedbackDto.FeedbackResponse>> getMyFeedback(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.ok(List.of());
        }
        List<FeedbackDto.FeedbackResponse> feedback = feedbackService.getUserFeedback(userDetails.getUsername());
        return ResponseEntity.ok(feedback);
    }
}
