package com.garage.security;

import com.garage.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final AppProperties props;

    public JwtService(AppProperties props) {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be set and at least 32 bytes long");
        }
        this.props = props;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Duration lifetime(boolean rememberMe) {
        return rememberMe
                ? Duration.ofDays(props.jwt().rememberDays())
                : Duration.ofHours(props.jwt().sessionHours());
    }

    private static final String VERSION_CLAIM = "tv";

    /** tokenVersion is stamped in the token so a logout-everywhere or password change revokes it instantly. */
    public String generate(UUID userId, long tokenVersion, boolean rememberMe) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(VERSION_CLAIM, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime(rememberMe))))
                .signWith(key)
                .compact();
    }

    /** The user id and the token's stamped version, or empty when the token is invalid, expired or pre-dates this change. */
    public Optional<Parsed> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Number version = claims.get(VERSION_CLAIM, Number.class);
            // A token issued before this feature existed has no "tv" claim: treated as version 0, like a fresh account.
            return Optional.of(new Parsed(UUID.fromString(claims.getSubject()), version == null ? 0 : version.longValue()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record Parsed(UUID userId, long tokenVersion) {
    }
}
