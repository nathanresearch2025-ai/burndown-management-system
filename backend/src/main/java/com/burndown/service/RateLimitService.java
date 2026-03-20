package com.burndown.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;

    public RateLimitService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check whether the rate limit has been exceeded.
     * @param key rate limit key (e.g. userId or projectId)
     * @param maxRequests maximum number of requests allowed
     * @param duration time window
     * @return true if within the limit, false if the limit has been exceeded
     */
    public boolean checkRateLimit(String key, int maxRequests, Duration duration) {
        try {
            String rateLimitKey = "rate_limit:" + key;
            Long currentCount = redisTemplate.opsForValue().increment(rateLimitKey);

            if (currentCount == null) {
                // Redis unavailable, fallback: allow the request.
                return true;
            }

            if (currentCount == 1) {
                // First request — set the expiry time.
                redisTemplate.expire(rateLimitKey, duration.toMillis(), TimeUnit.MILLISECONDS);
            }

            return currentCount <= maxRequests;
        } catch (Exception e) {
            // Redis exception, fallback: allow the request.
            return true;
        }
    }

    /**
     * Get the number of remaining allowed requests.
     */
    public long getRemainingRequests(String key, int maxRequests) {
        String rateLimitKey = "rate_limit:" + key;
        String countStr = redisTemplate.opsForValue().get(rateLimitKey);
        long currentCount = countStr != null ? Long.parseLong(countStr) : 0;
        return Math.max(0, maxRequests - currentCount);
    }

    /**
     * Get the time until the rate limit resets (in seconds).
     */
    public long getResetTime(String key) {
        String rateLimitKey = "rate_limit:" + key;
        Long ttl = redisTemplate.getExpire(rateLimitKey, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
}
