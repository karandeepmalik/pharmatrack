package com.pharma.inventory.controller;
import com.pharma.inventory.dto.AuthResponse;
import com.pharma.inventory.dto.LoginRequest;
import com.pharma.inventory.security.JwtService;
import com.pharma.inventory.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.web.bind.annotation.*;
import com.pharma.inventory.repository.UserRepository;
@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UserRepository userRepo;
    private final LoginRateLimiter loginRateLimiter;
    @Value("${app.cookie.secure:false}") private boolean cookieSecure;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        String clientIp = clientIp(request);
        if (loginRateLimiter.isBlocked(req.getUsername(), clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many failed login attempts. Please try again in a few minutes.");
        }
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(),req.getPassword()));
            UserDetails ud=userDetailsService.loadUserByUsername(req.getUsername());
            String token=jwtService.generateToken(ud.getUsername());
            var user=userRepo.findByUsername(req.getUsername()).orElseThrow();
            loginRateLimiter.recordSuccess(req.getUsername(), clientIp);
            response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie(token, jwtService.getExpirationMs() / 1000).toString());
            return ResponseEntity.ok(new AuthResponse(token,user.getUsername(),user.getFullName(),user.getRole().name()));
        } catch(BadCredentialsException|DisabledException|LockedException|InternalAuthenticationServiceException|UsernameNotFoundException e) {
            loginRateLimiter.recordFailure(req.getUsername(), clientIp);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }
    }

    // Cloud Run terminates TLS and proxies requests, so request.getRemoteAddr() is always the
    // load balancer's address — the real client IP is the first entry in X-Forwarded-For.
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, jwtCookie("", 0).toString());
        return ResponseEntity.noContent().build();
    }

    private ResponseCookie jwtCookie(String value, long maxAge) {
        return ResponseCookie.from("jwt", value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api")
                .maxAge(maxAge)
                .build();
    }
}
