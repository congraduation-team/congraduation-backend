package com.example.congraduation.auth;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

class InMemoryJwtRevocationStore implements JwtRevocationStore {

    private final ConcurrentHashMap<String, Instant> revokedTokens = new ConcurrentHashMap<>();

    @Override
    public boolean isRevoked(String tokenHash) {
        Instant expiresAt = revokedTokens.get(tokenHash);
        return expiresAt != null && Instant.now().isBefore(expiresAt);
    }

    @Override
    public void revoke(String tokenHash, Long studentId, Instant expiresAt) {
        revokedTokens.put(tokenHash, expiresAt);
    }
}
