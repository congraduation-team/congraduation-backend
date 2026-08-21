package com.example.congraduation.domain.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "revoked_jwts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_revoked_jwts_token_hash",
                columnNames = "token_hash"
        )
)
public class RevokedJwt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    protected RevokedJwt() {
    }

    private RevokedJwt(String tokenHash, Long studentId, Instant expiresAt, Instant revokedAt) {
        this.tokenHash = tokenHash;
        this.studentId = studentId;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
    }

    public static RevokedJwt create(String tokenHash, Long studentId, Instant expiresAt) {
        return new RevokedJwt(tokenHash, studentId, expiresAt, Instant.now());
    }

    public Long getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
