package com.pharma.inventory.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
@Service
public class JwtService {
    @Value("${jwt.secret}") private String secret;
    @Value("${jwt.expiration-ms}") private long expirationMs;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
    }

    public String generateToken(String username){
        return Jwts.builder().setSubject(username)
            .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis()+expirationMs))
            .signWith(getKey(),SignatureAlgorithm.HS256).compact();
    }
    public String extractUsername(String token){ return getClaims(token).getSubject(); }
    public boolean isValid(String token, UserDetails u){
        try{ return extractUsername(token).equals(u.getUsername()) && !getClaims(token).getExpiration().before(new Date()); }
        catch(JwtException e){ return false; }
    }
    private Claims getClaims(String token){ return Jwts.parserBuilder().setSigningKey(getKey()).build().parseClaimsJws(token).getBody(); }
    private Key getKey(){ return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }
}
