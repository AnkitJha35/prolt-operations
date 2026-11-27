package com.sense.prolt_operations.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.sense.prolt_operations.entity.UserRefreshSession;

import jakarta.persistence.LockModeType;

@Repository
public interface UserRefreshSessionRepository extends JpaRepository<UserRefreshSession, String> {

    Optional<UserRefreshSession> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserRefreshSession s WHERE s.tokenHash = :tokenHash")
    Optional<UserRefreshSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<UserRefreshSession> findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(String userId, LocalDateTime now);

    Optional<UserRefreshSession> findByIdAndUserId(String id, String userId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE UserRefreshSession s SET s.revokedAt = :now WHERE s.tokenFamilyId = :familyId AND s.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE UserRefreshSession s SET s.revokedAt = :now WHERE s.userId = :userId AND s.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM UserRefreshSession s WHERE s.expiresAt < :cutoff OR (s.revokedAt IS NOT NULL AND s.revokedAt < :cutoff)")
    int deleteExpiredOrRevokedOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
