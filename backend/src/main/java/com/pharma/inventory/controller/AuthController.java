package com.pharma.inventory.controller;
import com.pharma.inventory.dto.AuthResponse;
import com.pharma.inventory.dto.LoginRequest;
import com.pharma.inventory.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if(req.getUsername()==null||req.getUsername().isBlank()||req.getPassword()==null||req.getPassword().isBlank())
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username and password required");
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(),req.getPassword()));
            UserDetails ud=userDetailsService.loadUserByUsername(req.getUsername());
            String token=jwtService.generateToken(ud.getUsername());
            var user=userRepo.findByUsername(req.getUsername()).orElseThrow();
            return ResponseEntity.ok(new AuthResponse(token,user.getUsername(),user.getFullName(),user.getRole().name()));
        } catch(BadCredentialsException|DisabledException|LockedException|InternalAuthenticationServiceException|UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }
    }
}
