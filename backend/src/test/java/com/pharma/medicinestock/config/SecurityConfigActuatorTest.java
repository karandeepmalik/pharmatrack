package com.pharma.medicinestock.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Actuator endpoints beyond /health expose operational data (metrics, prometheus) and must not
 * be readable by any authenticated user — only ADMIN.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SecurityConfig — actuator endpoint access")
class SecurityConfigActuatorTest {

    @Autowired MockMvc mockMvc;

    @Nested @DisplayName("GET /actuator/health")
    class Health {

        @Test @DisplayName("is publicly accessible without authentication")
        void publiclyAccessible() throws Exception {
            mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }

    @Nested @DisplayName("GET /actuator/metrics")
    class Metrics {

        @Test @DisplayName("unauthenticated request is rejected")
        void unauthenticatedRejected() throws Exception {
            mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        }

        @Test @WithMockUser(roles = "USER") @DisplayName("regular USER role is forbidden")
        void userRoleForbidden() throws Exception {
            mockMvc.perform(get("/actuator/metrics")).andExpect(status().isForbidden());
        }

        @Test @WithMockUser(roles = "ADMIN") @DisplayName("ADMIN role is allowed")
        void adminRoleAllowed() throws Exception {
            mockMvc.perform(get("/actuator/metrics")).andExpect(status().isOk());
        }
    }

    @Nested @DisplayName("GET /actuator/prometheus")
    class Prometheus {

        @Test @DisplayName("unauthenticated request is rejected")
        void unauthenticatedRejected() throws Exception {
            mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        }

        @Test @WithMockUser(roles = "USER") @DisplayName("regular USER role is forbidden")
        void userRoleForbidden() throws Exception {
            mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
        }

        @Test @WithMockUser(roles = "ADMIN") @DisplayName("ADMIN role clears the security layer (endpoint itself is 404 — no micrometer-registry-prometheus dependency)")
        void adminRoleClearsSecurityLayer() throws Exception {
            // Confirms the security filter chain let an ADMIN request through rather than
            // blocking it — the 404 comes from the dispatcher, since this app only ships the
            // Stackdriver micrometer registry, not the Prometheus one. If that dependency is
            // ever added, this becomes a real 200 without any SecurityConfig change needed.
            mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isNotFound());
        }
    }

    @Nested @DisplayName("Security headers")
    class SecurityHeaders {

        @Test @DisplayName("HSTS is sent when the request arrives via X-Forwarded-Proto: https — "
                + "the real production shape, since Cloud Run terminates TLS upstream and proxies "
                + "plain HTTP, which would otherwise make request.isSecure() false and silently "
                + "suppress Spring Security's HSTS header writer")
        void hstsSentBehindForwardedProto() throws Exception {
            mockMvc.perform(get("/actuator/health").header("X-Forwarded-Proto", "https"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                    .andExpect(header().string("Strict-Transport-Security", containsString("includeSubDomains")));
        }

        @Test @DisplayName("CSP includes form-action 'none' — it has no fallback to default-src, "
                + "so it must be set explicitly or form submissions are left unrestricted")
        void cspIncludesFormAction() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Security-Policy", containsString("form-action 'none'")));
        }
    }
}
