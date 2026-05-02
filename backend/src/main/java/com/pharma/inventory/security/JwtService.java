package com.pharma.inventory.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import java.security.Key;
import java.util.Date;
@Service
public class JwtService {
    @Value("${jwt.secret}") private String secret;
    @Value("${jwt.expiration-ms}") private long expirationMs;
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
    private Key getKey(){ return Keys.hmacShaKeyFor(Decoders.BASE64.decode(java.util.Base64.getEncoder().encodeToString(secret.getBytes()))); }
}
