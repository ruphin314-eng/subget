package com.budget.subget_backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtils {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    // Clé chargée depuis une variable d'environnement (jwt.secret) - jamais en dur dans le code.
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateAccessToken(String email, String role) {
        return buildToken(email, role, TYPE_ACCESS, accessTokenExpirationMs);
    }

    public String generateRefreshToken(String email, String role) {
        return buildToken(email, role, TYPE_REFRESH, refreshTokenExpirationMs);
    }

    private String buildToken(String email, String role, String type, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    private String extractType(String token) {
        return parseClaims(token).get(CLAIM_TYPE, String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Un access token expiré/invalide échoue déjà via isTokenValid(). Ici on
    // vérifie en plus que le claim "type" correspond bien à un access token,
    // pour empêcher un refresh token d'être utilisé comme access token.
    public boolean isAccessToken(String token) {
        try {
            return TYPE_ACCESS.equals(extractType(token));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Symétrique : sur /auth/refresh-token, on n'accepte que des tokens
    // explicitement émis comme refresh token.
    public boolean isRefreshToken(String token) {
        try {
            return TYPE_REFRESH.equals(extractType(token));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
