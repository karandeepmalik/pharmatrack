package com.pharma.inventory.config;
import com.pharma.inventory.entity.User;
import com.pharma.inventory.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
@Configuration @RequiredArgsConstructor
public class AppConfig {
    private final UserRepository userRepository;
    @Bean public UserDetailsService userDetailsService(){
        return username -> {
            User user=userRepository.findByUsername(username)
                .orElseThrow(()->new UsernameNotFoundException("User not found: "+username));
            List<SimpleGrantedAuthority> auths = user.getRole()==User.Role.ADMIN
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),new SimpleGrantedAuthority("ROLE_USER"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
            return new org.springframework.security.core.userdetails.User(
                user.getUsername(),user.getPassword(),user.isActive(),true,true,true,auths);
        };
    }
    @Bean public PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(); }
}
