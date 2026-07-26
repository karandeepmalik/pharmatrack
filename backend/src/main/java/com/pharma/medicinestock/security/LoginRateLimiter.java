package com.pharma.medicinestock.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory lockout guarding against unlimited login brute-forcing, tracked along two
 * independent dimensions:
 *  - per-username: stops a classic brute force against one known account (e.g. "admin").
 *  - per-IP: stops a single source rotating through many usernames to dodge the per-username
 *    limit (credential stuffing / username enumeration), which per-username tracking alone
 *    cannot catch.
 *
 * Residual gap (unchanged by this): a distributed attacker spread across many IPs can still
 * grief-lock one specific username by tripping the per-username limit from each IP in turn —
 * fully closing that needs CAPTCHA/step-up auth, out of scope here.
 *
 * Not shared across Cloud Run instances — each instance tracks its own attempts — but still
 * meaningfully raises the cost of both attack shapes.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS_PER_USERNAME = 5;
    private static final int MAX_ATTEMPTS_PER_IP = 20;
    private static final long WINDOW_MS = 15 * 60 * 1000; // 15 minutes

    private static class Attempts {
        final AtomicInteger count = new AtomicInteger(0);
        volatile Instant windowStart = Instant.now();
    }

    private final ConcurrentHashMap<String, Attempts> attemptsByUsername = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Attempts> attemptsByIp = new ConcurrentHashMap<>();

    public boolean isBlocked(String username, String ip) {
        return isBlocked(attemptsByUsername, normalize(username), MAX_ATTEMPTS_PER_USERNAME)
                || isBlocked(attemptsByIp, normalize(ip), MAX_ATTEMPTS_PER_IP);
    }

    public void recordFailure(String username, String ip) {
        recordFailure(attemptsByUsername, normalize(username));
        recordFailure(attemptsByIp, normalize(ip));
    }

    public void recordSuccess(String username, String ip) {
        attemptsByUsername.remove(normalize(username));
        attemptsByIp.remove(normalize(ip));
    }

    private boolean isBlocked(ConcurrentHashMap<String, Attempts> map, String key, int max) {
        Attempts a = map.get(key);
        if (a == null) return false;
        if (windowExpired(a)) return false;
        return a.count.get() >= max;
    }

    private void recordFailure(ConcurrentHashMap<String, Attempts> map, String key) {
        map.compute(key, (k, existing) -> {
            if (existing == null || windowExpired(existing)) {
                Attempts fresh = new Attempts();
                fresh.count.set(1);
                return fresh;
            }
            existing.count.incrementAndGet();
            return existing;
        });
    }

    private boolean windowExpired(Attempts a) {
        return Instant.now().isAfter(a.windowStart.plusMillis(WINDOW_MS));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
