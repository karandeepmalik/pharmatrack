package com.pharma.medicinestock.controller;
import com.pharma.medicinestock.dto.RegisterRequest;
import com.pharma.medicinestock.dto.UserResponse;
import com.pharma.medicinestock.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/users") @RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    @GetMapping("/me") public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails ud){ return ResponseEntity.ok(userService.getByUsername(ud.getUsername())); }
    @GetMapping @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<List<UserResponse>> all(){ return ResponseEntity.ok(userService.getAll()); }
    @PostMapping @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<UserResponse> create(@Valid @RequestBody RegisterRequest req){ return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(req)); }
    @PostMapping("/{id}/toggle") @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<UserResponse> toggle(@PathVariable Long id){ return ResponseEntity.ok(userService.toggleActive(id)); }
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<Void> delete(@PathVariable Long id){ userService.deleteUser(id); return ResponseEntity.noContent().build(); }
    @PutMapping("/me/password") public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserDetails ud, @RequestBody Map<String,String> body){
        userService.changePassword(ud.getUsername(),body.get("currentPassword"),body.get("newPassword")); return ResponseEntity.ok().build();
    }
    @PutMapping("/{id}/password") @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<Void> adminChangePassword(@PathVariable Long id, @RequestBody Map<String,String> body){
        userService.adminChangePassword(id, body.get("newPassword")); return ResponseEntity.ok().build();
    }
}
