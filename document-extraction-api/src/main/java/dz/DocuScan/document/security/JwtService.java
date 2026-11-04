package dz.DocuScan.document.security;

import dz.DocuScan.document.config.SecurityProperties;
import dz.DocuScan.document.model.AuthenticatedUser;
import dz.DocuScan.document.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public record TokenClaims(UUID userId, UUID organizationId, Role role, int tokenVersion) {
    }

    private final SecurityProperties properties;
    private final SecretKey key;

    public JwtService(SecurityProperties properties) {
        this.properties = properties;
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET doit contenir au moins 32 octets.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issue(AuthenticatedUser user, int tokenVersion) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds());
        return Jwts.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.id().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("org", user.organizationId().toString())
                .claim("role", user.role().name())
                .claim("ver", tokenVersion)
                .signWith(key)
                .compact();
    }

    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.jwt().issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new TokenClaims(
                UUID.fromString(claims.getSubject()),
                UUID.fromString(claims.get("org", String.class)),
                Role.valueOf(claims.get("role", String.class)),
                claims.get("ver", Integer.class) == null ? 0 : claims.get("ver", Integer.class));
    }

    public long ttlSeconds() {
        return Math.max(60, properties.jwt().ttlMinutes() * 60);
    }
}
