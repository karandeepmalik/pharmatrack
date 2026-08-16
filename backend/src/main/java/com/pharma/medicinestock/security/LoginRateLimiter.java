package com.pharma.medicinestock.security;

import org.springframework.stereotype.Component;

/**
 * Lockout guarding against unlimited login brute-forcing, tracked along two independent
 * dimensions:
 *  - per-username: stops a classic brute force against one known account (e.g. "admin").
 *  - per-IP: stops a single source rotating through many usernames to dodge the per-username
 *    limit (credential stuffing / username enumeration), which per-username tracking alone
 *    cannot catch.
 *
 * Residual gap (unchanged by this): a distributed attacker spread across many IPs can still
 * grief-lock one specific username by tripping the per-username limit from each IP in turn —
 * fully closing that needs CAPTCHA/step-up auth, out of scope here.
 *
 * Attempt counting is delegated to a {@link RateLimitCounter} — {@link InMemoryRateLimitCounter}
 * by default (per-instance only), or {@link RedisRateLimitCounter} when {@code app.redis.enabled}
 * is set, sharing counts across every Cloud Run instance.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS_PER_USERNAME = 5;
    private static final int MAX_ATTEMPTS_PER_IP = 20;

    private final RateLimitCounter counter;

    public LoginRateLimiter(RateLimitCounter counter) {
        this.counter = counter;
    }

    public boolean isBlocked(String username, String ip) {
        return counter.isBlocked(usernameKey(username), MAX_ATTEMPTS_PER_USERNAME)
                || counter.isBlocked(ipKey(ip), MAX_ATTEMPTS_PER_IP);
    }

    public void recordFailure(String username, String ip) {
        counter.recordFailure(usernameKey(username));
        counter.recordFailure(ipKey(ip));
    }

    public void recordSuccess(String username, String ip) {
        counter.reset(usernameKey(username));
        counter.reset(ipKey(ip));
    }

    private String usernameKey(String username) {
        return "user:" + normalize(username);
    }

    private String ipKey(String ip) {
        return "ip:" + normalize(ip);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
