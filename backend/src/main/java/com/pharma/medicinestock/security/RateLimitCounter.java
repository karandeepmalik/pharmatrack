package com.pharma.medicinestock.security;

/**
 * Fixed-window attempt counter backing {@link LoginRateLimiter}. Two implementations exist:
 * {@link InMemoryRateLimitCounter} (default, per-instance only) and
 * {@link RedisRateLimitCounter} (shared across Cloud Run instances, active when
 * {@code app.redis.enabled=true}).
 */
public interface RateLimitCounter {

    boolean isBlocked(String key, int maxAttempts);

    void recordFailure(String key);

    void reset(String key);
}
