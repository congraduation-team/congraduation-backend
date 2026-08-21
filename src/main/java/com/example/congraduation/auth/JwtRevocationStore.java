package com.example.congraduation.auth;

import java.time.Instant;

public interface JwtRevocationStore {

    boolean isRevoked(String tokenHash);

    void revoke(String tokenHash, Long studentId, Instant expiresAt);
}
