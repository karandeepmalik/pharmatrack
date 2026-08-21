package com.pharma.medicinestock.config;
import com.pharma.medicinestock.entity.User;
import com.pharma.medicinestock.repository.UserRepository;
import com.pharma.medicinestock.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration @RequiredArgsConstructor
public class AppConfig {
    private final UserRepository userRepository;
    @Bean public UserDetailsService userDetailsService(){
        return username -> {
            User user=userRepository.findByUsername(username)
                .orElseThrow(()->new UsernameNotFoundException("User not found: "+username));
            return new AppUserDetails(user);
        };
    }
    @Bean public PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(); }
}
