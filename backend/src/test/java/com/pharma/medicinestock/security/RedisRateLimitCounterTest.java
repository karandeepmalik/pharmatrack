package com.pharma.medicinestock.security;

import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("RedisRateLimitCounter")
class RedisRateLimitCounterTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    private RedisRateLimitCounter counter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        counter = new RedisRateLimitCounter(redisTemplate);
    }

    @Test
    @DisplayName("first failure increments and sets a 15-minute TTL")
    void firstFailureSetsExpiry() {
        when(valueOperations.increment("ratelimit:user:admin")).thenReturn(1L);

        counter.recordFailure("user:admin");

        verify(redisTemplate).expire("ratelimit:user:admin", Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("subsequent failures within the window do not reset the TTL")
    void laterFailureDoesNotResetExpiry() {
        when(valueOperations.increment("ratelimit:user:admin")).thenReturn(3L);

        counter.recordFailure("user:admin");

        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("isBlocked compares the stored count against the threshold")
    void isBlockedComparesAgainstThreshold() {
        when(valueOperations.get("ratelimit:user:admin")).thenReturn("5");

        assertThat(counter.isBlocked("user:admin", 5)).isTrue();
        assertThat(counter.isBlocked("user:admin", 6)).isFalse();
    }

    @Test
    @DisplayName("isBlocked is false when no attempts have been recorded")
    void isBlockedFalseWhenNoKey() {
        when(valueOperations.get("ratelimit:user:admin")).thenReturn(null);

        assertThat(counter.isBlocked("user:admin", 1)).isFalse();
    }

    @Test
    @DisplayName("reset deletes the key")
    void resetDeletesKey() {
        counter.reset("user:admin");

        verify(redisTemplate).delete("ratelimit:user:admin");
    }

    @Test
    @DisplayName("Redis errors fail open instead of blocking login")
    void redisErrorsFailOpen() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("connection refused"));
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertThat(counter.isBlocked("user:admin", 1)).isFalse();
        assertThatCode(() -> counter.recordFailure("user:admin")).doesNotThrowAnyException();
        assertThatCode(() -> counter.reset("user:admin")).doesNotThrowAnyException();
    }
}
