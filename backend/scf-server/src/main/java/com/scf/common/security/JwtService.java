package com.scf.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
            @Value("${scf.jwt.secret}") String secret,
            @Value("${scf.jwt.expiration-ms}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserContext context) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(context.userId())
                .claims(Map.of(
                        "loginName", context.loginName(),
                        "operatorId", nullSafe(context.operatorId()),
                        "projectId", nullSafe(context.projectId()),
                        "enterpriseId", nullSafe(context.enterpriseId()),
                        "roleId", nullSafe(context.roleId()),
                        "identityId", nullSafe(context.identityId())
                ))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public UserContext parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new UserContext(
                claims.getSubject(),
                claims.get("loginName", String.class),
                claims.get("operatorId", String.class),
                claims.get("projectId", String.class),
                claims.get("enterpriseId", String.class),
                claims.get("roleId", String.class),
                claims.get("identityId", String.class)
        );
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
