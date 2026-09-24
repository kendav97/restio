package com.restio.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Issues and verifies the signed access tokens (JWT, HMAC-SHA). */
@Component
public class JwtService {

    static final String CLAIM_RESTAURANTS = "restaurants";
    static final String CLAIM_ROLES = "roles";
    static final String CLAIM_PERMISSIONS = "perms";
    static final String CLAIM_DEVICE = "dev";

    private final SecretKey key;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String issue(AuthenticatedUser user, Duration ttl) {
        Instant now = clock.instant();
        var builder =
                Jwts.builder()
                        .subject(user.getName())
                        .claim(CLAIM_RESTAURANTS, List.copyOf(user.restaurantIds()))
                        .claim(CLAIM_ROLES, List.copyOf(user.roles()))
                        .claim(CLAIM_PERMISSIONS, List.copyOf(user.permissions()))
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(now.plus(ttl)));
        if (user.deviceId() != null) {
            builder.claim(CLAIM_DEVICE, user.deviceId());
        }
        return builder.signWith(key).compact();
    }

    /**
     * Verifies the signature and expiry of a token.
     *
     * @throws InvalidTokenException if the token is malformed, tampered with or expired
     */
    public AuthenticatedUser verify(String token) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(key)
                            .clock(() -> Date.from(clock.instant()))
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
            Number device = claims.get(CLAIM_DEVICE, Number.class);
            String subject = claims.getSubject();
            return new AuthenticatedUser(
                    subject.startsWith(AuthenticatedUser.DEVICE_NAME_PREFIX)
                            ? null
                            : Long.valueOf(subject),
                    longs(claims.get(CLAIM_RESTAURANTS, Collection.class)),
                    strings(claims.get(CLAIM_ROLES, Collection.class)),
                    strings(claims.get(CLAIM_PERMISSIONS, Collection.class)),
                    device == null ? null : device.longValue());
        } catch (JwtException
                | IllegalArgumentException
                | ClassCastException
                | NullPointerException ex) {
            throw new InvalidTokenException(ex);
        }
    }

    private static Set<Long> longs(Collection<?> values) {
        return values == null
                ? Set.of()
                : values.stream()
                        .map(v -> ((Number) v).longValue())
                        .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> strings(Collection<?> values) {
        return values == null
                ? Set.of()
                : values.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    /** The token cannot be trusted. The cause is kept for logs, never sent to the client. */
    public static class InvalidTokenException extends RuntimeException {
        InvalidTokenException(Throwable cause) {
            super("Invalid access token", cause);
        }
    }
}
