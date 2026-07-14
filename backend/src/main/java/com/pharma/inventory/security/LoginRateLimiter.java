package com.pharma.inventory.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory per-username lockout guarding against unlimited login brute-forcing. Not shared
 * across Cloud Run instances — each instance tracks its own attempts — but still meaningfully
 * raises the cost of a credential-stuffing attempt against a known username (e.g. "admin").
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 15 * 60 * 1000; // 15 minutes

    private static class Attempts {
        final AtomicInteger count = new AtomicInteger(0);
        volatile Instant windowStart = Instant.now();
    }

    private final ConcurrentHashMap<String, Attempts> attemptsByUsername = new ConcurrentHashMap<>();

    public boolean isBlocked(String username) {
        Attempts a = attemptsByUsername.get(normalize(username));
        if (a == null) return false;
        if (windowExpired(a)) return false;
        return a.count.get() >= MAX_ATTEMPTS;
    }

    public void recordFailure(String username) {
        attemptsByUsername.compute(normalize(username), (key, existing) -> {
            if (existing == null || windowExpired(existing)) {
                Attempts fresh = new Attempts();
                fresh.count.set(1);
                return fresh;
            }
            existing.count.incrementAndGet();
            return existing;
        });
    }

    public void recordSuccess(String username) {
        attemptsByUsername.remove(normalize(username));
    }

    private boolean windowExpired(Attempts a) {
        return Instant.now().isAfter(a.windowStart.plusMillis(WINDOW_MS));
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
