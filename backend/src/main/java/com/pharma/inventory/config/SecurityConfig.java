package com.pharma.inventory.config;
import com.pharma.inventory.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.Arrays;
import java.util.List;
@Configuration @EnableWebSecurity @EnableMethodSecurity @RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    @Value("${cors.allowed-origins:http://localhost:3000}") private String allowedOrigins;
    @Bean public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
            .cors(c->c.configurationSource(corsConfigSource()))
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a
                .requestMatchers("/api/auth/**","/actuator/health").permitAll()
                .anyRequest().authenticated())
            .authenticationProvider(authProvider())
            .addFilterBefore(jwtAuthFilter,UsernamePasswordAuthenticationFilter.class)
            .build();
    }
    @Bean public AuthenticationProvider authProvider(){
        DaoAuthenticationProvider p=new DaoAuthenticationProvider();
        p.setUserDetailsService(userDetailsService); p.setPasswordEncoder(passwordEncoder); return p;
    }
    @Bean public AuthenticationManager authManager(AuthenticationConfiguration c) throws Exception{ return c.getAuthenticationManager(); }
    @Bean public CorsConfigurationSource corsConfigSource(){
        CorsConfiguration cfg=new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*")); cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src=new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**",cfg); return src;
    }
}
