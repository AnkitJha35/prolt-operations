package com.sense.prolt_operations.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sense.prolt_operations.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {

    @Query("SELECT u FROM User u WHERE LOWER(TRIM(u.username)) = LOWER(TRIM(:username))")
    Optional<User> findByNormalizedUsername(@Param("username") String username);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(TRIM(u.username)) = LOWER(TRIM(:username))")
    boolean existsByNormalizedUsername(@Param("username") String username);

    @Query("SELECT u FROM User u WHERE LOWER(TRIM(u.username)) = LOWER(TRIM(:username))")
    Optional<User> findByUsername(@Param("username") String username);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(TRIM(u.username)) = LOWER(TRIM(:username))")
    boolean existsByUsername(@Param("username") String username);

    @Query("SELECT u FROM User u WHERE LOWER(TRIM(u.email)) = LOWER(TRIM(:email))")
    Optional<User> findByNormalizedEmail(@Param("email") String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(TRIM(u.email)) = LOWER(TRIM(:email))")
    boolean existsByNormalizedEmail(@Param("email") String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(TRIM(u.email)) = LOWER(TRIM(:email)) AND u.id <> :id")
    boolean existsByNormalizedEmailAndIdNot(@Param("email") String email, @Param("id") String id);

    @Query("SELECT u FROM User u WHERE LOWER(TRIM(u.email)) = LOWER(TRIM(:email))")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(TRIM(u.email)) = LOWER(TRIM(:email))")
    boolean existsByEmail(@Param("email") String email);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.enabled = true")
    long countByRoleAndEnabledTrue(@Param("role") com.sense.prolt_operations.entity.Role role);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.role = :role AND u.enabled = true ORDER BY u.id")
    java.util.List<User> findActiveUsersByRoleForUpdate(@Param("role") com.sense.prolt_operations.entity.Role role);

    @Query("SELECT new com.sense.prolt_operations.repository.UserSecurityState(u.id, u.username, u.role, u.enabled, u.tokenVersion) FROM User u WHERE u.id = :id")
    Optional<UserSecurityState> findSecurityStateById(@Param("id") String id);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.tokenVersion = u.tokenVersion + 1 WHERE u.id = :id")
    int incrementTokenVersion(@Param("id") String id);

    java.util.List<User> findAllByOrderByCreatedAtDesc();
}
