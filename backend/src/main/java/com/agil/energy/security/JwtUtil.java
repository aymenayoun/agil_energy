package com.agil.energy.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Issue a JWT carrying user id, role, station id, and region.
     * stationId is set for STATION_MANAGER; region is set for MANAGER; both null for ADMIN.
     */
    public String generateToken(UserDetails userDetails, Long userId, String role,
                                Long stationId, String region) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("stationId", stationId);
        claims.put("region", region);
        claims.put("type", "access");
        String jti = UUID.randomUUID().toString();
        return createToken(claims, userDetails.getUsername(), jti);
    }

    private String createToken(Map<String, Object> claims, String subject, String jti) {
        return Jwts.builder()
                .id(jti)
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token)  { return extractClaim(token, Claims::getSubject); }
    public Long   extractUserId(String token)    { return extractClaim(token, c -> c.get("userId",    Long.class));   }
    public String extractRole(String token)      { return extractClaim(token, c -> c.get("role",      String.class)); }
    public Long   extractStationId(String token) { return extractClaim(token, c -> c.get("stationId", Long.class));   }
    public String extractRegion(String token)    { return extractClaim(token, c -> c.get("region",    String.class)); }
    public String extractJti(String token)       { return extractClaim(token, Claims::getId); }
    public Date   extractExpiration(String token){ return extractClaim(token, Claims::getExpiration); }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public long getAccessTokenExpirationMs() { return expiration; }
}