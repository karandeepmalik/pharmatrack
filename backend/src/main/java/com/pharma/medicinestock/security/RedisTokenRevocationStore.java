package com.pharma.medicinestock.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed {@link TokenRevocationStore}, shared across all Cloud Run instances. Active when
 * {@code app.redis.enabled=true}. Mirrors {@link RedisRateLimitCounter}: Redis (self-hosted on
 * the same low-memory VM as Postgres) is not on the critical path for request auth, so a Redis
 * error is logged and treated as "not revoked" rather than propagated — a Redis hiccup degrades
 * logout to client-side-only for that request instead of breaking every authenticated request.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true")
public class RedisTokenRevocationStore implements TokenRevocationStore {

    private static final String KEY_PREFIX = "revoked-jwt:";

    private final StringRedisTemplate redisTemplate;

    public RedisTokenRevocationStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void revoke(String jti, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) return; // already expired — nothing to revoke
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        } catch (Exception e) {
            log.warn("Redis unavailable while revoking token '{}', logout will be client-side only", jti, e);
        }
    }

    @Override
    public boolean isRevoked(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (Exception e) {
            log.warn("Redis unavailable for revocation check on '{}', failing open", jti, e);
            return false;
        }
    }
}
