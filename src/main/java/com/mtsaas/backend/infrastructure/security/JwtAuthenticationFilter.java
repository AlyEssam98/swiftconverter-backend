package com.mtsaas.backend.infrastructure.security;

import com.mtsaas.backend.application.service.SecurityUser;
import com.mtsaas.backend.infrastructure.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import io.jsonwebtoken.ExpiredJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            jwt = authHeader.substring(7);
            
            // Handle mock development token
            if ("mock-development-token".equals(jwt)) {
                // Create a mock user for development
                var mockUser = com.mtsaas.backend.domain.User.builder()
                    .id(java.util.UUID.randomUUID())
                    .email("mock@example.com")
                    .passwordHash("mock")
                    .role(com.mtsaas.backend.domain.Role.USER)
                    .credits(20L)
                    .emailVerified(true)
                    .build();
                
                UserDetails userDetails = new SecurityUser(mockUser);
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                filterChain.doFilter(request, response);
                return;
            }
            
            userEmail = jwtService.extractUsername(jwt);
            
            // Check if token is blacklisted (logged out)
            if (tokenBlacklistService.isBlacklisted(jwt)) {
                LOGGER.info("Token is blacklisted (user logged out)");
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"Token has been revoked\"");
                try {
                    response.getWriter().write("{\"error\":\"token_revoked\",\"message\":\"Token has been revoked\"}");
                } catch (IOException io) {
                    LOGGER.warn("Failed to write response body for blacklisted token", io);
                }
                return;
            }
            
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                var user = userRepository.findByEmail(userEmail).orElse(null);
                if (user != null) {
                    UserDetails userDetails = new SecurityUser(user);
                    if (jwtService.isTokenValid(jwt, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities());
                        authToken.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        } catch (ExpiredJwtException eje) {
            // Token is expired: force the client to re-authenticate
            LOGGER.info("JWT expired: {}", eje.getMessage());
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"The token expired\"");
            try {
                response.getWriter().write("{\"error\":\"token_expired\",\"message\":\"JWT expired\"}");
            } catch (IOException io) {
                LOGGER.warn("Failed to write response body for expired token", io);
            }
            return;
        } catch (Exception e) {
            // Log the error but don't fail the request - let it proceed as anonymous
            // The SecurityConfig will handle 401 if the endpoint requires auth
            LOGGER.warn("JWT Authentication error: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }
}
