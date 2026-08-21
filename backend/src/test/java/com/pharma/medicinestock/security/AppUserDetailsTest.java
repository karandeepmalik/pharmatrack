package com.pharma.medicinestock.security;

import com.pharma.medicinestock.entity.User;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AppUserDetails")
class AppUserDetailsTest {

    private User user(User.Role role, boolean active) {
        return User.builder()
                .id(1L).username("john.doe").password("hashed-pw")
                .fullName("John Doe").email("j@j.com").role(role).active(active).build();
    }

    @Test
    @DisplayName("exposes username/password straight from the wrapped User")
    void exposesUsernameAndPassword() {
        AppUserDetails details = new AppUserDetails(user(User.Role.USER, true));
        assertThat(details.getUsername()).isEqualTo("john.doe");
        assertThat(details.getPassword()).isEqualTo("hashed-pw");
    }

    @Test
    @DisplayName("getUser() returns the exact wrapped User, for reading fullName/role without a second lookup")
    void getUserReturnsWrappedEntity() {
        User user = user(User.Role.ADMIN, true);
        AppUserDetails details = new AppUserDetails(user);
        assertThat(details.getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("isEnabled() reflects the User's active flag")
    void isEnabledReflectsActiveFlag() {
        assertThat(new AppUserDetails(user(User.Role.USER, true)).isEnabled()).isTrue();
        assertThat(new AppUserDetails(user(User.Role.USER, false)).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("account/credentials expiry and lock flags are always true (not modeled by this app)")
    void otherFlagsAlwaysTrue() {
        AppUserDetails details = new AppUserDetails(user(User.Role.USER, true));
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }

    @Test
    @DisplayName("ADMIN role grants both ROLE_ADMIN and ROLE_USER authorities")
    void adminGrantsBothAuthorities() {
        AppUserDetails details = new AppUserDetails(user(User.Role.ADMIN, true));
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    @DisplayName("USER role grants only ROLE_USER")
    void userGrantsOnlyUserAuthority() {
        AppUserDetails details = new AppUserDetails(user(User.Role.USER, true));
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
    }
}
