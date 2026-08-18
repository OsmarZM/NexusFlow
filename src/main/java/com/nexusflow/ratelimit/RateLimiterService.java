package com.nexusflow.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    @Value("${nexusflow.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${nexusflow.rate-limit.capacity:100}")
    private int defaultCapacity;

    public RateLimitResult tryConsume(String clientKey) {
        return tryConsume(clientKey, defaultCapacity);
    }

    public RateLimitResult tryConsume(String clientKey, int maxRequestsPerMinute) {
        if (!enabled) {
            return new RateLimitResult(true, maxRequestsPerMinute, maxRequestsPerMinute);
        }

        try {
            long currentMinute = Instant.now().getEpochSecond() / 60;
            String redisKey = "ratelimit:" + clientKey + ":" + currentMinute;

            Long currentCount = redisTemplate.opsForValue().increment(redisKey);
            if (currentCount != null && currentCount == 1) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(65));
            }

            long current = currentCount != null ? currentCount : 1;
            boolean allowed = current <= maxRequestsPerMinute;
            long remaining = Math.max(0, maxRequestsPerMinute - current);

            if (!allowed) {
                log.warn("Rate limit exceeded for client [{}]: count={}, max={}", clientKey, current, maxRequestsPerMinute);
            }

            return new RateLimitResult(allowed, maxRequestsPerMinute, remaining);
        } catch (Exception e) {
            log.warn("Rate limiter Redis failure (fail-open strategy applied): {}", e.getMessage());
            return new RateLimitResult(true, maxRequestsPerMinute, maxRequestsPerMinute);
        }
    }

    public record RateLimitResult(
            boolean allowed,
            long limit,
            long remaining
    ) {}
}
