package com.brokerx.interfaces.rest;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TokenService {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Duration ttl;

    public TokenService(Duration ttl) {
        this.ttl = Objects.requireNonNull(ttl, "ttl");
    }

    public AuthToken issue(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        Instant issuedAt = Instant.now();
        Instant expireAt = issuedAt.plus(ttl);
        String payload = accountId + ":" + UUID.randomUUID() + ":" + expireAt.toEpochMilli();
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        sessions.put(token, new Session(accountId, issuedAt, expireAt));
        return new AuthToken(token, issuedAt, expireAt);
    }

    public Optional<TokenPrincipal> authenticate(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return Optional.empty();
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expireAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(new TokenPrincipal(session.accountId(), session.issuedAt(), session.expireAt(), token));
    }

    public TokenPrincipal require(String authorizationHeader) {
        return authenticate(authorizationHeader)
                .orElseThrow(() -> new RestException(HttpURLConnection.HTTP_UNAUTHORIZED, "Token invalide ou expire"));
    }

    public void revoke(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public record AuthToken(String token, Instant issuedAt, Instant expireAt) { }

    private record Session(UUID accountId, Instant issuedAt, Instant expireAt) { }
}

