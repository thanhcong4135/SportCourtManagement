package com.sportcourt.auth.repository;

import com.sportcourt.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUser_IdAndRevokedAtIsNullAndExpiresAtBefore(UUID userId, OffsetDateTime cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update RefreshToken rt
        set rt.revokedAt = :revokedAt
        where rt.user.id = :userId
          and rt.revokedAt is null
        """)
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("revokedAt") OffsetDateTime revokedAt);
}
