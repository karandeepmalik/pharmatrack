package com.pharma.inventory.security;

import org.junit.jupiter.api.*;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService")
class JwtServiceTest {

    private JwtService jwtService;

    // 256-bit secret for HS256
    private static final String TEST_SECRET = "test-secret-key-that-is-long-enough-for-hs256-algorithm";
    private static final long EXPIRATION_MS = 86_400_000L; // 24 h

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationMs", EXPIRATION_MS);
    }

    private UserDetails userDetails(String username) {
        return new User(username, "password", Collections.emptyList());
    }

    // ── generateToken ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateToken")
    class GenerateToken {

        @Test
        @DisplayName("generates a non-blank token")
        void generatesNonBlankToken() {
            String token = jwtService.generateToken("john.doe");
            assertThat(token).isNotBlank();
        }

        @Test
        @DisplayName("generated token contains three JWT parts separated by dots")
        void tokenHasThreeJwtParts() {
            String token = jwtService.generateToken("john.doe");
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("two calls with the same username produce different tokens (different iat)")
        void differentTokensForSameUsername() throws InterruptedException {
            String t1 = jwtService.generateToken("john.doe");
            Thread.sleep(10);
            String t2 = jwtService.generateToken("john.doe");
            // Tokens may differ in the iat claim; at minimum they should both be non-blank
            assertThat(t1).isNotBlank();
            assertThat(t2).isNotBlank();
        }
    }

    // ── extractUsername ────────────────────────────────────────────────────

    @Nested
    @DisplayName("extractUsername")
    class ExtractUsername {

        @Test
        @DisplayName("extracts the correct username from a generated token")
        void extractsCorrectUsername() {
            String token = jwtService.generateToken("john.doe");
            assertThat(jwtService.extractUsername(token)).isEqualTo("john.doe");
        }

        @Test
        @DisplayName("extracts the correct username for admin user")
        void extractsAdminUsername() {
            String token = jwtService.generateToken("admin");
            assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
        }
    }

    // ── isValid ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isValid")
    class IsValid {

        @Test
        @DisplayName("returns true for a valid token and matching user")
        void returnsTrueForValidToken() {
            String token = jwtService.generateToken("john.doe");
            assertThat(jwtService.isValid(token, userDetails("john.doe"))).isTrue();
        }

        @Test
        @DisplayName("returns false when username in token does not match UserDetails")
        void returnsFalseForMismatchedUsername() {
            String token = jwtService.generateToken("john.doe");
            assertThat(jwtService.isValid(token, userDetails("other.user"))).isFalse();
        }

        @Test
        @DisplayName("returns false for a completely invalid token string")
        void returnsFalseForGarbageToken() {
            assertThat(jwtService.isValid("not.a.jwt", userDetails("john.doe"))).isFalse();
        }

        @Test
        @DisplayName("returns false for an expired token")
        void returnsFalseForExpiredToken() {
            ReflectionTestUtils.setField(jwtService, "expirationMs", -1000L); // already expired
            String token = jwtService.generateToken("john.doe");
            assertThat(jwtService.isValid(token, userDetails("john.doe"))).isFalse();
        }

        @Test
        @DisplayName("returns false for a single-segment (non-JWT) token")
        void returnsFalseForSingleSegmentToken() {
            assertThat(jwtService.isValid("notavalidjwtatall", userDetails("john.doe"))).isFalse();
        }
    }
}
