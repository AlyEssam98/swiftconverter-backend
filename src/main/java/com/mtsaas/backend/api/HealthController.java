package com.mtsaas.backend.api;

import com.mtsaas.backend.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class HealthController {

    private final UserRepository userRepository;

    @GetMapping
    @SuppressWarnings("unused")
    public ResponseEntity<Map<String, String>> health() {
        // Simple DB check to wake up database if sleeping
        try {
            userRepository.count();
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN", "error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}