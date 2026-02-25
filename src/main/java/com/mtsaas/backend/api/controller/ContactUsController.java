package com.mtsaas.backend.api.controller;

import com.mtsaas.backend.application.dto.ContactUsDto;
import com.mtsaas.backend.application.service.ContactUsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/contact-us")
@RequiredArgsConstructor
@Slf4j
public class ContactUsController {

    private final ContactUsService contactUsService;

    @PostMapping
    public ResponseEntity<?> submitContactUs(
            @Valid @RequestBody ContactUsDto.ContactUsRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        try {
            log.info("Contact Us submission - Name: {}, Email: {}, Subject: {}", request.getName(), request.getEmail(), request.getSubject());
            String email = userDetails != null ? userDetails.getUsername() : null;
            ContactUsDto.ContactUsResponse response = contactUsService.submitContactUs(email, request, httpRequest);
            log.info("Contact Us submission successful - ID: {}", response.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing contact us request: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process contact form", "details", e.getMessage()));
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, Object> errors = new HashMap<>();
        errors.put("error", "Validation failed");
        errors.put("message", "Please check the following fields:");
        
        // Collect all field errors, allowing multiple messages per field
        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> {
            String fieldName = error.getField();
            String message = error.getDefaultMessage();
            // If field already has an error, combine them with semicolon
            if (fieldErrors.containsKey(fieldName)) {
                fieldErrors.put(fieldName, fieldErrors.get(fieldName) + "; " + message);
            } else {
                fieldErrors.put(fieldName, message);
            }
        });
        
        errors.put("fields", fieldErrors);
        log.warn("Validation error for Contact Us: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    @GetMapping("/my")
    public ResponseEntity<List<ContactUsDto.ContactUsResponse>> getMyContactUs(
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.ok(List.of());
        }
        List<ContactUsDto.ContactUsResponse> contactUs = contactUsService.getUserContactUs(userDetails.getUsername());
        return ResponseEntity.ok(contactUs);
    }
}
