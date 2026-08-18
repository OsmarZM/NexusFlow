package com.nexusflow.unit.ratelimit;

import com.nexusflow.ratelimit.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(rateLimiterService, "enabled", true);
        ReflectionTestUtils.setField(rateLimiterService, "defaultCapacity", 100);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Should allow request when count is within rate limit capacity")
    void shouldAllowRequestWithinLimit() {
        when(valueOperations.increment(anyString())).thenReturn(50L);

        RateLimiterService.RateLimitResult result = rateLimiterService.tryConsume("ip:192.168.1.1", 100);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(100);
        assertThat(result.remaining()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should reject request when count exceeds rate limit capacity")
    void shouldRejectRequestExceedingLimit() {
        when(valueOperations.increment(anyString())).thenReturn(101L);

        RateLimiterService.RateLimitResult result = rateLimiterService.tryConsume("ip:192.168.1.1", 100);

        assertThat(result.allowed()).isFalse();
        assertThat(result.limit()).isEqualTo(100);
        assertThat(result.remaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should fail-open when Redis throws an unexpected exception")
    void shouldFailOpenOnRedisException() {
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis connection timed out"));

        RateLimiterService.RateLimitResult result = rateLimiterService.tryConsume("ip:192.168.1.1", 100);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(100);
    }
}
