package com.mtsaas.backend.infrastructure.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Date;

/**
 * In-memory token blacklist service for logout functionality.
 * Blacklisted tokens are stored with their expiration time and cleaned up periodically.
 */
@Service
public class TokenBlacklistService {

    private final Map<String, Long> blacklistedTokens = new ConcurrentHashMap<>();

    /**
     * Add a token to the blacklist
     * @param token The JWT token to blacklist
     * @param expirationTime The expiration time of the token in milliseconds
     */
    public void blacklistToken(String token, long expirationTime) {
        blacklistedTokens.put(token, expirationTime);
    }

    /**
     * Check if a token is blacklisted
     * @param token The JWT token to check
     * @return true if the token is blacklisted, false otherwise
     */
    public boolean isBlacklisted(String token) {
        return blacklistedTokens.containsKey(token);
    }

    /**
     * Scheduled cleanup of expired tokens from the blacklist
     * Runs every hour to remove tokens that have naturally expired
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupExpiredTokens() {
        long currentTime = System.currentTimeMillis();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }
}
