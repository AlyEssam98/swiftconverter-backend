package com.mtsaas.backend.api;

import com.mtsaas.backend.application.dto.AuthDto;
import com.mtsaas.backend.application.service.AuthService;
import com.mtsaas.backend.infrastructure.email.EmailService;
import com.mtsaas.backend.infrastructure.security.JwtService;
import com.mtsaas.backend.infrastructure.security.TokenBlacklistService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final EmailService emailService;

    @Value("${app.jwt.refresh-token-expiration-ms:1209600000}")
    private long refreshTokenExpirationMs;

    @Value("${app.jwt.refresh-cookie-name:refresh_token}")
    private String refreshCookieName;

    @Value("${app.jwt.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    @Value("${app.jwt.refresh-cookie-same-site:Lax}")
    private String refreshCookieSameSite;

    @PostMapping("/register")
    public ResponseEntity<AuthDto.AuthenticationResponse> register(
            @RequestBody AuthDto.RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthDto.AuthenticationResponse> authenticate(
            @RequestBody AuthDto.AuthenticationRequest request) {
        AuthService.AuthSession authSession = authService.authenticate(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, createRefreshCookie(authSession.getRefreshToken()))
                .body(authSession.getResponse());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDto.AuthenticationResponse> refresh(HttpServletRequest request) {
        String refreshToken = readCookie(request, refreshCookieName);
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401)
                    .header(HttpHeaders.SET_COOKIE, clearRefreshCookie())
                    .build();
        }

        try {
            AuthService.AuthSession authSession = authService.refresh(refreshToken);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, createRefreshCookie(authSession.getRefreshToken()))
                    .body(authSession.getResponse());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(401)
                    .header(HttpHeaders.SET_COOKIE, clearRefreshCookie())
                    .build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // Get token expiration and blacklist it
            long expirationTime = jwtService.extractExpirationTime(token);
            tokenBlacklistService.blacklistToken(token, expirationTime);
        }

        String refreshToken = readCookie(request, refreshCookieName);
        authService.logout(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie())
                .build();
    }

    private String createRefreshCookie(String tokenValue) {
        return ResponseCookie.from(refreshCookieName, tokenValue)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/v1/auth")
                .maxAge(refreshTokenExpirationMs / 1000)
                .build()
                .toString();
    }

    private String clearRefreshCookie() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/api/v1/auth")
                .maxAge(0)
                .build()
                .toString();
    }

    private String readCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthDto.AuthenticationResponse> verifyEmail(
            @RequestBody AuthDto.VerifyEmailRequest request) {
        AuthService.AuthSession authSession = authService.verifyEmail(request.getToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, createRefreshCookie(authSession.getRefreshToken()))
                .body(authSession.getResponse());
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(
            @RequestBody AuthDto.ResendVerificationRequest request) {
        authService.resendVerification(request.getEmail());
        return ResponseEntity.ok().build();
    }

}
