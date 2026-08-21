package com.pharma.medicinestock.security;

import com.pharma.medicinestock.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Wraps the full {@link User} entity instead of just username/password/authorities — lets
 * {@code AuthController.login()} read the authenticated principal straight back out of
 * {@code AuthenticationManager.authenticate()}'s result (fullName, role, everything needed for
 * the login response) instead of re-fetching the same user from the DB a second and third time.
 * Every DB round trip here crosses from Cloud Run (asia-south1) to the self-hosted Postgres VM
 * (us-central1), so collapsing 3 lookups into 1 is a real, measurable latency win, not just tidiness.
 */
public class AppUserDetails implements UserDetails {

    private final User user;

    public AppUserDetails(User user) {
        this.user = user;
    }

    public User getUser() { return user; }

    @Override public String getUsername() { return user.getUsername(); }
    @Override public String getPassword() { return user.getPassword(); }
    @Override public boolean isEnabled() { return user.isActive(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRole() == User.Role.ADMIN
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
