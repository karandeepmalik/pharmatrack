package com.pharma.medicinestock.security;

import java.time.Duration;

/**
 * Server-side revocation for an otherwise-stateless JWT, keyed by its {@code jti} claim —
 * what makes {@code POST /auth/logout} actually invalidate a token instead of relying purely
 * on the frontend discarding it from localStorage. Two implementations exist, mirroring
 * {@link RateLimitCounter}: {@link NoopTokenRevocationStore} (default, no shared state without
 * Redis) and {@link RedisTokenRevocationStore} (active when {@code app.redis.enabled=true}),
 * shared across every Cloud Run instance.
 */
public interface TokenRevocationStore {

    /** Marks {@code jti} as revoked until {@code ttl} elapses — pass the token's remaining validity. */
    void revoke(String jti, Duration ttl);

    boolean isRevoked(String jti);
}
