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

@DisplayName("RedisTokenRevocationStore")
class RedisTokenRevocationStoreTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    private RedisTokenRevocationStore store;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        store = new RedisTokenRevocationStore(redisTemplate);
    }

    @Test
    @DisplayName("revoke sets a key with the given TTL")
    void revokeSetsKeyWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        store.revoke("jti-123", Duration.ofMinutes(30));

        verify(valueOperations).set("revoked-jwt:jti-123", "1", Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("revoke with a zero or negative TTL is a no-op (token already expired)")
    void revokeWithNonPositiveTtlIsNoop() {
        store.revoke("jti-123", Duration.ZERO);
        store.revoke("jti-456", Duration.ofSeconds(-5));

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("isRevoked reflects whether the key exists")
    void isRevokedReflectsKeyExistence() {
        when(redisTemplate.hasKey("revoked-jwt:jti-123")).thenReturn(true);
        when(redisTemplate.hasKey("revoked-jwt:jti-456")).thenReturn(false);

        assertThat(store.isRevoked("jti-123")).isTrue();
        assertThat(store.isRevoked("jti-456")).isFalse();
    }

    @Test
    @DisplayName("isRevoked treats a null hasKey result as not revoked")
    void isRevokedNullTreatedAsFalse() {
        when(redisTemplate.hasKey("revoked-jwt:jti-123")).thenReturn(null);

        assertThat(store.isRevoked("jti-123")).isFalse();
    }

    @Test
    @DisplayName("Redis errors fail open instead of blocking authentication")
    void redisErrorsFailOpen() {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("connection refused"));
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("connection refused"));

        assertThat(store.isRevoked("jti-123")).isFalse();
        assertThatCode(() -> store.revoke("jti-123", Duration.ofMinutes(30))).doesNotThrowAnyException();
    }
}
