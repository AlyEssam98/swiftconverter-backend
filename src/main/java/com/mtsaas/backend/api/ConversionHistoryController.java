package com.mtsaas.backend.api;

import com.mtsaas.backend.domain.Conversion;
import com.mtsaas.backend.infrastructure.repository.ConversionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversions")
@RequiredArgsConstructor
public class ConversionHistoryController {

    private final ConversionRepository conversionRepository;

    @GetMapping("/history")
    public List<Conversion> getConversionHistory() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            throw new SecurityException("Authentication required");
        }

        return conversionRepository.findByUserEmailOrderByCreatedAtDesc(auth.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteConversion(@PathVariable UUID id) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new String[] { "Authentication required" });
        }

        var conversion = conversionRepository.findById(Objects.requireNonNull(id)).orElse(null);
        if (conversion == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new String[] { "Conversion not found" });
        }

        var userEmail = conversion.getUser() != null ? conversion.getUser().getEmail() : null;
        if (userEmail == null || !userEmail.equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new String[] { "Cannot delete another user's conversion" });
        }

        conversionRepository.deleteById(Objects.requireNonNull(id));
        return ResponseEntity.ok().build();
    }
}
