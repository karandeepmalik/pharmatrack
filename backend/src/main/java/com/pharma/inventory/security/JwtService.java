package com.pharma.inventory.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
@Service
public class JwtService {
    @Value("${jwt.secret}") private String secret;
    @Value("${jwt.expiration-ms}") private long expirationMs;

    /** The literal placeholder shipped in application.properties — must never be used as a real signing key. */
    private static final String INSECURE_DEFAULT = "change-me-in-production-must-be-at-least-256-bits-long";

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        if (INSECURE_DEFAULT.equals(secret)) {
            throw new IllegalStateException(
                "jwt.secret is still the insecure placeholder from application.properties — " +
                "set the JWT_SECRET environment variable to a real, private secret before starting the app");
        }
    }

    public String generateToken(String username){
        return Jwts.builder().subject(username)
            .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis()+expirationMs))
            .signWith(getKey(), Jwts.SIG.HS256).compact();
    }
    public String extractUsername(String token){ return getClaims(token).getSubject(); }
    public boolean isValid(String token, UserDetails u){
        try{ return extractUsername(token).equals(u.getUsername()) && !getClaims(token).getExpiration().before(new Date()); }
        catch(JwtException e){ return false; }
    }
    public long getExpirationMs() { return expirationMs; }
    private Claims getClaims(String token){ return Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token).getPayload(); }
    private SecretKey getKey(){ return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }
}
