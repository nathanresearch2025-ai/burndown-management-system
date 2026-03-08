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
     * 检查是否超过速率限制
     * @param key 限流键（例如：userId 或 projectId）
     * @param maxRequests 最大请求数
     * @param duration 时间窗口
     * @return true 如果未超限，false 如果已超限
     */
    public boolean checkRateLimit(String key, int maxRequests, Duration duration) {
        try {
            String rateLimitKey = "rate_limit:" + key;
            Long currentCount = redisTemplate.opsForValue().increment(rateLimitKey);

            if (currentCount == null) {
                // Redis 不可用，降级处理：允许请求
                return true;
            }

            if (currentCount == 1) {
                // 第一次请求，设置过期时间
                redisTemplate.expire(rateLimitKey, duration.toMillis(), TimeUnit.MILLISECONDS);
            }

            return currentCount <= maxRequests;
        } catch (Exception e) {
            // Redis 异常，降级处理：允许请求
            return true;
        }
    }

    /**
     * 获取剩余请求次数
     */
    public long getRemainingRequests(String key, int maxRequests) {
        String rateLimitKey = "rate_limit:" + key;
        String countStr = redisTemplate.opsForValue().get(rateLimitKey);
        long currentCount = countStr != null ? Long.parseLong(countStr) : 0;
        return Math.max(0, maxRequests - currentCount);
    }

    /**
     * 获取重置时间（秒）
     */
    public long getResetTime(String key) {
        String rateLimitKey = "rate_limit:" + key;
        Long ttl = redisTemplate.getExpire(rateLimitKey, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
}
