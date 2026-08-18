package com.pharma.medicinestock.security;

import org.junit.jupiter.api.*;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@DisplayName("NoopTokenRevocationStore")
class NoopTokenRevocationStoreTest {

    private final NoopTokenRevocationStore store = new NoopTokenRevocationStore();

    @Test
    @DisplayName("revoke is a no-op and never throws")
    void revokeIsNoop() {
        assertThatCode(() -> store.revoke("jti-123", Duration.ofMinutes(30))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("isRevoked always returns false — no shared state without Redis")
    void isRevokedAlwaysFalse() {
        store.revoke("jti-123", Duration.ofMinutes(30));
        assertThat(store.isRevoked("jti-123")).isFalse();
    }
}
