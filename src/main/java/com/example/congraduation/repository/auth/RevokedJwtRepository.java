package com.example.congraduation.repository.auth;

import com.example.congraduation.domain.auth.RevokedJwt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevokedJwtRepository extends JpaRepository<RevokedJwt, Long> {

    boolean existsByTokenHash(String tokenHash);
}
