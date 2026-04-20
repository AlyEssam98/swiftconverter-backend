package com.mtsaas.backend.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Checks if a request is allowed based on the rate limit.
     * 
     * @param key             The unique identifier for the action/user (e.g., "resend_email:user@example.com")
     * @param limit           The maximum number of allowed requests in the time window
     * @param windowInSeconds The time window in seconds
     * @return true if the request is allowed, false if the rate limit is exceeded
     */
    public boolean isAllowed(String key, int limit, long windowInSeconds) {
        String countStr = redisTemplate.opsForValue().get(key);
        
        if (countStr == null) {
            // First request in the window
            redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(windowInSeconds));
            return true;
        }

        int count = Integer.parseInt(countStr);
        if (count >= limit) {
            // Rate limit exceeded
            return false;
        }

        // Increment the count (keeps the existing TTL)
        redisTemplate.opsForValue().increment(key);
        return true;
    }
}
