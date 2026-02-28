package com.mtsaas.backend.api;

import com.mtsaas.backend.domain.User;
import com.mtsaas.backend.domain.Role;
import com.mtsaas.backend.infrastructure.repository.UserRepository;
import com.mtsaas.backend.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/oauth2")
@RequiredArgsConstructor
@Slf4j
public class OAuth2Controller {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @GetMapping("/callback")
    @org.springframework.transaction.annotation.Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
    public ResponseEntity<?> oauth2Callback(@AuthenticationPrincipal OAuth2User oauth2User, 
                                             OAuth2AuthenticationToken authentication) {
        try {
            String provider = authentication.getAuthorizedClientRegistrationId();
            String email = oauth2User.getAttribute("email");
            String name = oauth2User.getAttribute("name");
            String firstName = oauth2User.getAttribute("given_name");
            String lastName = oauth2User.getAttribute("family_name");
            
            // Fallback for providers that might have name in different format
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email not provided by OAuth2 provider"));
            }
            
            log.info("OAuth2 callback from {} for email: {}", provider, email);
            
            // Use pessimistic write lock to prevent race conditions during concurrent requests
            Optional<User> existingUser = userRepository.findByEmailForUpdate(email);
            User user;
            
            if (existingUser.isPresent()) {
                user = existingUser.get();
                log.info("Existing user found in OAuth2 callback: {}", user.getEmail());
            } else {
                // Create new user with OAuth2 info
                user = new User();
                user.setEmail(email);
                user.setFirstName(firstName != null ? firstName : (name != null && !name.isEmpty() ? name.split(" ")[0] : ""));
                user.setLastName(lastName != null ? lastName : (name != null && name.split(" ").length > 1 ? name.split(" ")[1] : ""));
                user.setProvider(provider != null ? provider.toUpperCase() : "OAUTH");
                user.setRole(Role.USER);
                user.setPasswordHash(""); // No password for OAuth2 users
                user.setCredits(0); // Will track credits via CreditPurchase records
                user.setEmailVerified(true); // OAuth2 emails are verified
                
                try {
                    user = userRepository.save(user);
                    log.info("✓ New OAuth2 user created in callback: {} (ID: {}) via {}", user.getEmail(), user.getId(), user.getProvider());
                } catch (DataIntegrityViolationException e) {
                    // Should not happen with pessimistic locking, but handle as backup
                    log.error("⚠ DataIntegrityViolationException in OAuth2 callback for {}: {}", email, e.getMessage());
                    user = userRepository.findByEmail(email)
                            .orElseThrow(() -> new RuntimeException("OAuth2 user creation failed: " + e.getMessage()));
                }
            }
            
            // Generate JWT token
            String token = jwtService.generateToken(user.getEmail());
            
            Map<String, Object> respMap = new HashMap<>();
            respMap.put("token", token);
            respMap.put("user", Map.of(
                "email", user.getEmail(),
                "firstName", user.getFirstName() != null ? user.getFirstName() : "",
                "lastName", user.getLastName() != null ? user.getLastName() : "",
                "credits", user.getCredits()
            ));
            
            return ResponseEntity.ok(respMap);
        } catch (Exception e) {
            log.error("OAuth2 callback error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Authentication failed: " + e.getMessage()));
        }
    }

    @GetMapping("/success")
    public String oauth2Success() {
        return "<html><body><script>" +
               "if (window.opener && window.opener !== window) {" +
               "  window.opener.postMessage({type: 'OAUTH_SUCCESS', token: window.location.hash.substring(1)}, '*');" +
               "  window.close();" +
               "} else {" +
               "  // Fallback for non-popup flow" +
               "  window.location.href = '/auth/callback';" +
               "}" +
               "</script></body></html>";
    }
    
    @GetMapping("/error")
    public String oauth2Error() {
        return "<html><body><script>" +
               "if (window.opener && window.opener !== window) {" +
               "  window.opener.postMessage({type: 'OAUTH_ERROR', error: 'OAuth authentication failed'}, '*');" +
               "  window.close();" +
               "} else {" +
               "  // Fallback for non-popup flow" +
               "  window.location.href = '/auth/login?error=oauth_failed';" +
               "}" +
               "</script></body></html>";
    }
}
