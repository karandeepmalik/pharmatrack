package com.pharma.medicinestock.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Default {@link TokenRevocationStore}: no shared state without Redis, so revocation can't be
 * enforced consistently across Cloud Run instances — {@code POST /auth/logout} degrades to
 * exactly today's behavior (the frontend discards its own token, nothing enforced server-side)
 * rather than pretending to revoke via a per-instance-only store that a second instance would
 * never see. Active whenever {@code app.redis.enabled} is not {@code true} (local dev without
 * Redis, and all tests, since {@code application-test.properties} never sets it).
 */
@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopTokenRevocationStore implements TokenRevocationStore {

    @Override
    public void revoke(String jti, Duration ttl) {
        // No-op — see class Javadoc.
    }

    @Override
    public boolean isRevoked(String jti) {
        return false;
    }
}
