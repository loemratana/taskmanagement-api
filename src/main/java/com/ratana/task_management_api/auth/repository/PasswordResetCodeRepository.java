package com.ratana.task_management_api.auth.repository;

import com.ratana.task_management_api.auth.entity.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

    /** The newest code for a user that is still usable (not used, not expired). */
    @Query("""
            SELECT c FROM PasswordResetCode c
            WHERE c.user.id = :userId AND c.usedAt IS NULL AND c.expiresAt > :now
            ORDER BY c.createdAt DESC
            LIMIT 1
            """)
    Optional<PasswordResetCode> findLatestUsable(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Invalidates every outstanding code, so only the newest one emailed can work. */
    @Modifying
    @Query("UPDATE PasswordResetCode c SET c.usedAt = :now WHERE c.user.id = :userId AND c.usedAt IS NULL")
    int invalidateAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM PasswordResetCode c WHERE c.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
