package com.pharma.medicinestock.security;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
@Component @RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String token = extractToken(req);
        if (token != null) {
            try {
                String username = jwtService.extractUsername(token);
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails u = userDetailsService.loadUserByUsername(username);
                    if (jwtService.isValid(token, u)) {
                        var auth = new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
                // Expired, malformed, tampered, or stale (deleted-user) token — treat the request as
                // unauthenticated rather than letting the exception abort it. This matters most for
                // public endpoints like /auth/login: the frontend's Axios interceptor attaches
                // whatever token sits in localStorage to every outgoing request, including a fresh
                // login attempt — a leftover expired token from days ago must never prevent that
                // login from reaching AuthController and being evaluated on its own merits. This was
                // a real, reported bug: correct credentials rejected as "Invalid username or
                // password" purely because an unrelated stale token crashed the filter chain first.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }

    private String extractToken(HttpServletRequest req) {
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if ("jwt".equals(c.getName())) return c.getValue();
            }
        }
        // Fallback for API clients that send the Authorization header directly
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header.substring(7);
        return null;
    }
}
