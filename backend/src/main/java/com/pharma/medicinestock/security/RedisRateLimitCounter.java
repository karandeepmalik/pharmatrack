package com.pharma.medicinestock.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed {@link RateLimitCounter}, shared across all Cloud Run instances — closes the gap
 * called out in the old single-instance {@code LoginRateLimiter} (an attacker rotating across
 * instances could dodge a per-instance limit). Active when {@code app.redis.enabled=true}.
 *
 * <p>Redis (self-hosted on the same low-memory VM as Postgres) is not on the critical path for
 * login: any Redis error is logged and treated as "not blocked" / a no-op rather than propagated,
 * so a Redis hiccup degrades rate limiting instead of taking down login entirely.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisRateLimitCounter implements RateLimitCounter {

    private static final String KEY_PREFIX = "ratelimit:";
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitCounter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isBlocked(String key, int maxAttempts) {
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + key);
            return value != null && Integer.parseInt(value) >= maxAttempts;
        } catch (Exception e) {
            log.warn("Redis unavailable for rate-limit check on '{}', failing open", key, e);
            return false;
        }
    }

    @Override
    public void recordFailure(String key) {
        try {
            String redisKey = KEY_PREFIX + key;
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redisTemplate.expire(redisKey, WINDOW);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for rate-limit recordFailure on '{}', skipping", key, e);
        }
    }

    @Override
    public void reset(String key) {
        try {
            redisTemplate.delete(KEY_PREFIX + key);
        } catch (Exception e) {
            log.warn("Redis unavailable for rate-limit reset on '{}', skipping", key, e);
        }
    }
}
