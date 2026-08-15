package com.pharma.medicinestock.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A stale/invalid token must never abort the request it's attached to — see JwtAuthenticationFilter's
 * catch-block comment. This was a real, reported production bug: a leftover expired token in the
 * browser's localStorage is attached (via the frontend's Axios request interceptor) to every outgoing
 * request, including a fresh POST /auth/login — and an uncaught exception here previously killed that
 * request before AuthController ever evaluated the submitted credentials, surfacing as "Invalid
 * username or password" despite the credentials being correct.
 */
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "test-secret-key-that-is-long-enough-for-hs256-algorithm";

    private JwtService jwtService;
    private UserDetailsService userDetailsService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationMs", 86_400_000L);
        userDetailsService = mock(UserDetailsService.class);
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UserDetails userDetails(String username) {
        return new User(username, "password", Collections.emptyList());
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        return req;
    }

    @Test
    @DisplayName("an expired token does not throw and lets the request continue unauthenticated")
    void expiredTokenDoesNotAbortRequest() throws Exception {
        ReflectionTestUtils.setField(jwtService, "expirationMs", -1000L); // already expired
        String expiredToken = jwtService.generateToken("john.doe");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 86_400_000L);

        MockHttpServletRequest req = requestWithBearer(expiredToken);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        assertThatNoException().isThrownBy(() -> filter.doFilterInternal(req, res, chain));

        verify(chain, times(1)).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("a malformed token does not throw and lets the request continue unauthenticated")
    void malformedTokenDoesNotAbortRequest() throws Exception {
        MockHttpServletRequest req = requestWithBearer("not-a-real-jwt-at-all");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        assertThatNoException().isThrownBy(() -> filter.doFilterInternal(req, res, chain));

        verify(chain, times(1)).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("a token for a since-deleted user does not throw and lets the request continue unauthenticated")
    void tokenForDeletedUserDoesNotAbortRequest() throws Exception {
        String token = jwtService.generateToken("ghost.user");
        when(userDetailsService.loadUserByUsername("ghost.user"))
                .thenThrow(new UsernameNotFoundException("ghost.user"));

        MockHttpServletRequest req = requestWithBearer(token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        assertThatNoException().isThrownBy(() -> filter.doFilterInternal(req, res, chain));

        verify(chain, times(1)).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("a valid token still authenticates the request as before")
    void validTokenStillAuthenticates() throws Exception {
        String token = jwtService.generateToken("john.doe");
        when(userDetailsService.loadUserByUsername("john.doe")).thenReturn(userDetails("john.doe"));

        MockHttpServletRequest req = requestWithBearer(token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("john.doe");
        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    @DisplayName("no token present leaves the request unauthenticated and still calls the chain")
    void noTokenLeavesUnauthenticated() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(1)).doFilter(req, res);
    }
}
