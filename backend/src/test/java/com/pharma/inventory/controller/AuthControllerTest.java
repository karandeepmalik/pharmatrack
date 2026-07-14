package com.pharma.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharma.inventory.config.AppConfig;
import com.pharma.inventory.config.SecurityConfig;
import com.pharma.inventory.dto.LoginRequest;
import com.pharma.inventory.entity.User;
import com.pharma.inventory.repository.UserRepository;
import com.pharma.inventory.security.JwtService;
import com.pharma.inventory.security.LoginRateLimiter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.*;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AppConfig.class, LoginRateLimiter.class})
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired LoginRateLimiter loginRateLimiter;

    @MockBean AuthenticationManager authenticationManager;
    @MockBean JwtService jwtService;
    @MockBean UserRepository userRepository;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(2L).username("john.doe").fullName("John Doe")
                .email("j@j.com").role(User.Role.USER)
                .active(true).password("hashed").build();
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);
    }

    private String json(String username, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return objectMapper.writeValueAsString(req);
    }

    // ── POST /api/auth/login ───────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("returns 200 and sets HttpOnly jwt cookie on valid credentials")
        void returnsOkAndSetsCookieOnValidCredentials() throws Exception {
            when(authenticationManager.authenticate(any())).thenReturn(
                    new UsernamePasswordAuthenticationToken("john.doe", null));
            when(userRepository.findByUsername("john.doe")).thenReturn(Optional.of(activeUser));
            when(jwtService.generateToken("john.doe")).thenReturn("test.jwt.token");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("john.doe", "secret")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Set-Cookie", containsString("jwt=test.jwt.token")))
                    .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                    .andExpect(header().string("Set-Cookie", containsString("Path=/api")))
                    .andExpect(jsonPath("$.token").value("test.jwt.token"))
                    .andExpect(jsonPath("$.username").value("john.doe"))
                    .andExpect(jsonPath("$.fullName").value("John Doe"))
                    .andExpect(jsonPath("$.role").value("USER"));
        }

        @Test
        @DisplayName("returns 401 on bad credentials")
        void returns401OnBadCredentials() throws Exception {
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("bad credentials"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("john.doe", "wrong")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns 401 when account is disabled")
        void returns401WhenAccountDisabled() throws Exception {
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new DisabledException("disabled"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("john.doe", "secret")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns 400 when username is blank")
        void returns400WhenUsernameBlank() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("", "secret")))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authenticationManager);
        }

        @Test
        @DisplayName("returns 400 when password is blank")
        void returns400WhenPasswordBlank() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("john.doe", "")))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authenticationManager);
        }

        @Test
        @DisplayName("returns ADMIN role in response for admin users")
        void returnsAdminRole() throws Exception {
            User admin = User.builder().id(1L).username("admin").fullName("System Admin")
                    .email("a@a.com").role(User.Role.ADMIN).active(true).password("hashed").build();

            when(authenticationManager.authenticate(any())).thenReturn(
                    new UsernamePasswordAuthenticationToken("admin", null));
            when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(jwtService.generateToken("admin")).thenReturn("admin.jwt.token");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("admin", "adminpass")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        @DisplayName("login endpoint is accessible without prior authentication")
        void loginEndpointIsPublic() throws Exception {
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("bad"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("x", "y")))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── Login rate limiting ────────────────────────────────────────────────
    // Each test uses its own dedicated username: LoginRateLimiter is a real singleton bean
    // shared across the cached Spring context for this test class, so reusing a username
    // from another test method here would leak lockout state between tests.

    @Nested
    @DisplayName("Login rate limiting")
    class LoginRateLimiting {

        @Test
        @DisplayName("5th consecutive failure for a username still returns 401")
        void fifthFailureStillReturns401() throws Exception {
            String username = "ratelimit.fifth";
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("bad"));

            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(username, "wrong")))
                        .andExpect(status().isUnauthorized());
            }
        }

        @Test
        @DisplayName("6th consecutive failure for the same username returns 429")
        void sixthFailureReturns429() throws Exception {
            String username = "ratelimit.sixth";
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("bad"));

            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(username, "wrong")))
                        .andExpect(status().isUnauthorized());
            }

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(username, "wrong")))
                    .andExpect(status().isTooManyRequests());

            // Authentication must not even be attempted once blocked
            verify(authenticationManager, times(5)).authenticate(any());
        }

        @Test
        @DisplayName("lockout is scoped per-username — a different username is unaffected")
        void lockoutDoesNotAffectOtherUsernames() throws Exception {
            String blockedUser = "ratelimit.blocked";
            String otherUser = "ratelimit.other";

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("bad"));
            for (int i = 0; i < 6; i++) {
                mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(blockedUser, "wrong")));
            }

            reset(authenticationManager, userRepository, jwtService);
            when(authenticationManager.authenticate(any())).thenReturn(
                    new UsernamePasswordAuthenticationToken(otherUser, null));
            when(userRepository.findByUsername(otherUser)).thenReturn(Optional.of(activeUser));
            when(jwtService.generateToken(otherUser)).thenReturn("other.jwt.token");
            when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(otherUser, "secret")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a successful login resets the failure count")
        void successfulLoginResetsCount() throws Exception {
            String username = "ratelimit.reset";

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("bad"));
            for (int i = 0; i < 4; i++) {
                mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(username, "wrong")));
            }

            reset(authenticationManager, userRepository, jwtService);
            when(authenticationManager.authenticate(any())).thenReturn(
                    new UsernamePasswordAuthenticationToken(username, null));
            when(userRepository.findByUsername(username)).thenReturn(Optional.of(activeUser));
            when(jwtService.generateToken(username)).thenReturn("reset.jwt.token");
            when(jwtService.getExpirationMs()).thenReturn(86_400_000L);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(username, "secret")))
                    .andExpect(status().isOk());

            // Failures resumed from zero — a further 4 failures (not yet a 6th total) still 401
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("bad"));
            for (int i = 0; i < 4; i++) {
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(username, "wrong")))
                        .andExpect(status().isUnauthorized());
            }
        }
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/logout")
    class Logout {

        @Test
        @DisplayName("returns 204 and clears jwt cookie")
        void returns204AndClearsCookie() throws Exception {
            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Set-Cookie", containsString("jwt=")))
                    .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        }

        @Test
        @DisplayName("logout endpoint is accessible without authentication")
        void logoutIsPublic() throws Exception {
            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isNoContent());
        }
    }
}
