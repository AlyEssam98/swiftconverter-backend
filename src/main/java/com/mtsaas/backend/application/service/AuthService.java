package com.mtsaas.backend.application.service;

import com.mtsaas.backend.application.dto.AuthDto;
import com.mtsaas.backend.domain.Role;
import com.mtsaas.backend.domain.User;
import com.mtsaas.backend.infrastructure.repository.UserRepository;
import com.mtsaas.backend.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;
        private final CreditService creditService;
        private final EmailVerificationService emailVerificationService;

        @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE)
        public AuthDto.AuthenticationResponse register(AuthDto.RegisterRequest request) {
                log.info("Registering new user: {}", request.getEmail());
                
                String email = request.getEmail();
                User user = null;
                
                // Use pessimistic write lock to prevent race conditions
                // This locks the row if user exists, or allows proceed if doesn't exist
                Optional<User> existingUser = userRepository.findByEmailForUpdate(email);
                if (existingUser.isPresent()) {
                        log.warn("User already exists for email: {}", email);
                        throw new IllegalArgumentException("Email already registered");
                }
                
                // Create new user with all fields set
                user = new User();
                user.setEmail(email);
                user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                user.setRole(Role.USER);
                user.setCredits(0);  // Will track credits via CreditPurchase records
                user.setEmailVerified(false);
                user.setProvider("LOCAL");
                
                // Generate verification token
                String verificationToken = UUID.randomUUID().toString();
                user.setEmailVerificationToken(verificationToken);
                user.setEmailVerificationTokenExpiry(LocalDateTime.now().plusHours(24)); // Token expires in 24 hours

                try {
                        user = userRepository.save(user);
                        log.info("✓ User created successfully: {} (ID: {})", user.getEmail(), user.getId());
                        
                        // Send verification email
                        emailVerificationService.sendVerificationEmail(email, verificationToken);
                        log.info("✓ Verification email sent to: {}", email);
                        
                } catch (DataIntegrityViolationException e) {
                        // This should not happen with pessimistic locking, but handle it as backup
                        log.error("⚠ DataIntegrityViolationException during user creation for {}: {}", email, e.getMessage());
                        // Try to fetch the user that was created (race condition scenario)
                        user = userRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("User creation failed: " + e.getMessage()));
                }

                // Don't give free credits until email is verified
                log.info("✓ User registration pending email verification: {}", email);
                
                // Return message instead of token - user must verify email first
                return new AuthDto.AuthenticationResponse(null, "Check your email for a verification link. The link will expire in 24 hours.");
        }

        public AuthDto.AuthenticationResponse authenticate(AuthDto.AuthenticationRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                request.getEmail(),
                                                request.getPassword()));
                var user = userRepository.findByEmail(request.getEmail())
                                .orElseThrow();
                var jwtToken = jwtService.generateToken(new SecurityUser(user));
                return new AuthDto.AuthenticationResponse(jwtToken, "");
        }
}
