package com.example.hauiTrash.security;

import com.example.hauiTrash.entity.AccountRole;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String SECRET;

    @Value("${jwt.expiration}") // milliseconds
    private long EXPIRATION;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Integer accountId, AccountRole role) {
        Date now = new Date();
        Date exp = new Date(System.currentTimeMillis() + EXPIRATION);

        return Jwts.builder()
                .setSubject(String.valueOf(accountId))
                .claim("role", role.name())
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Integer getAccountId(String token) {
        return Integer.parseInt(parse(token).getSubject());
    }

    public String getRole(String token) {
        Object r = parse(token).get("role");
        return r == null ? null : r.toString();
    }

    public long getExpirationSeconds() {
        return EXPIRATION / 1000;
    }

    private Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
