package com.pharma.medicinestock.controller;
import com.pharma.medicinestock.dto.AuthResponse;
import com.pharma.medicinestock.dto.LoginRequest;
import com.pharma.medicinestock.security.JwtService;
import com.pharma.medicinestock.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.web.bind.annotation.*;
import com.pharma.medicinestock.repository.UserRepository;
@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UserRepository userRepo;
    private final LoginRateLimiter loginRateLimiter;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
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

    // Stateless JWT — nothing to invalidate server-side. The frontend discards its own token.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
