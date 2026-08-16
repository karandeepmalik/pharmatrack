package com.pharma.medicinestock.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default {@link RateLimitCounter}: per-instance only, not shared across Cloud Run instances.
 * Active whenever {@code app.redis.enabled} is not {@code true} (local dev without Redis, and
 * all tests, since {@code application-test.properties} never sets it).
 */
@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRateLimitCounter implements RateLimitCounter {

    private static final long WINDOW_MS = 15 * 60 * 1000; // 15 minutes

    private static class Attempts {
        final AtomicInteger count = new AtomicInteger(0);
        volatile Instant windowStart = Instant.now();
    }

    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

    @Override
    public boolean isBlocked(String key, int maxAttempts) {
        Attempts a = attempts.get(key);
        if (a == null || windowExpired(a)) return false;
        return a.count.get() >= maxAttempts;
    }

    @Override
    public void recordFailure(String key) {
        attempts.compute(key, (k, existing) -> {
            if (existing == null || windowExpired(existing)) {
                Attempts fresh = new Attempts();
                fresh.count.set(1);
                return fresh;
            }
            existing.count.incrementAndGet();
            return existing;
        });
    }

    @Override
    public void reset(String key) {
        attempts.remove(key);
    }

    private boolean windowExpired(Attempts a) {
        return Instant.now().isAfter(a.windowStart.plusMillis(WINDOW_MS));
    }
}
