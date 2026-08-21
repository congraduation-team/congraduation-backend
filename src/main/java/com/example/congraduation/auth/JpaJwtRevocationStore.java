package com.example.congraduation.auth;

import com.example.congraduation.domain.auth.RevokedJwt;
import com.example.congraduation.repository.auth.RevokedJwtRepository;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaJwtRevocationStore implements JwtRevocationStore {

    private final RevokedJwtRepository revokedJwtRepository;

    public JpaJwtRevocationStore(RevokedJwtRepository revokedJwtRepository) {
        this.revokedJwtRepository = revokedJwtRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRevoked(String tokenHash) {
        return revokedJwtRepository.existsByTokenHash(tokenHash);
    }

    @Override
    @Transactional
    public void revoke(String tokenHash, Long studentId, Instant expiresAt) {
        if (revokedJwtRepository.existsByTokenHash(tokenHash)) {
            return;
        }
        try {
            revokedJwtRepository.save(RevokedJwt.create(tokenHash, studentId, expiresAt));
        } catch (DataIntegrityViolationException ignored) {
            // 같은 토큰으로 동시에 로그아웃한 경우
        }
    }
}
