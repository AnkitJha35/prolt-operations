# Milestone V16B: User Lifecycle & Privilege Revocation Hardening

**Project**: `prolt-operations`  
**Date**: November 3, 2026  
**Status**: COMPLETED  
**Scope**: Backend Security Hardening & Concurrency Protection  

---

## 1. Executive Summary

Milestone **V16B** delivers enterprise-grade security hardening for user lifecycle management and privilege revocation in the `prolt-operations` Spring Boot application. It directly addresses the security vulnerabilities and architectural gaps identified in the V16 backend audit (`docs/54-v16-backend-security-architecture-audit.md`):

1. **Self-Deletion & Self-Disabling Prevention**: Authenticated users can no longer delete or disable their own accounts.
2. **Last Active `SUPER_ADMIN` Protection**: The backend enforces invariant protection preventing the deletion, demotion, or disabling of the final remaining active Super Administrator account.
3. **Immediate Access & Refresh Token Revocation on Privilege Modifications**: Changes to authorization-defining attributes (`role`, `hiddenModules`, `readOnlyModules`) automatically increment `tokenVersion`, revoke all active refresh token sessions in PostgreSQL, and update Redis security state immediately.
4. **Preservation of Active Sessions for Non-Security Profile Updates**: Harmless profile edits (`fullName`, `email`) preserve `tokenVersion` and active user sessions without forcing premature logout.
5. **Deadlock-Free Concurrency Control**: Concurrency safety during privilege updates, deletions, and status toggles is enforced via JPA pessimistic write locking (`SELECT ... FOR UPDATE`) ordered deterministically by primary key (`ORDER BY u.id`), eliminating race conditions and deadlocks under concurrent requests.
6. **Transactional Audit Logging Integrity**: Rejection audit logs (`DELETE_REJECTED`, `UPDATE_REJECTED`) are persisted using an independent transaction (`Propagation.REQUIRES_NEW`), guaranteeing audit persistence even when operational exceptions trigger rollback on the calling transaction.
7. **Cache & State Consistency**: Redis security cache evictions are synchronized with PostgreSQL database transaction commit via `TransactionSynchronizationManager.registerSynchronization(afterCommit())`, and cache TTL is aligned to 15 minutes (matching access token lifetime).

All 277 backend integration tests passed with zero failures.

---

## 2. Pre-Implementation Findings Verification

Prior to Milestone V16B, code analysis of `UserService.java`, `UserController.java`, and `UserRepository.java` revealed the following critical vulnerabilities:

| Vulnerability ID | Description | Severity | Prior State |
|---|---|---|---|
| **VULN-V16B-01** | Last `SUPER_ADMIN` Deletion Vulnerability | Critical | `deleteUser` performed no check on role or remaining count; deleting the last active Super Admin left the system unmanageable. |
| **VULN-V16B-02** | Last `SUPER_ADMIN` Demotion Vulnerability | Critical | `updateUser` allowed changing a Super Admin's role to `ADMIN` or `CLIENT` even if they were the only active Super Admin. |
| **VULN-V16B-03** | Self-Deletion Vulnerability | High | `deleteUser` did not check if `principal.getId()` matched target ID; users could delete their own accounts while authenticated. |
| **VULN-V16B-04** | Module Privilege Desynchronization | High | Changes to `hiddenModules` and `readOnlyModules` did not increment `tokenVersion` or revoke refresh sessions; clients retained old access permissions until JWT expired. |
| **VULN-V16B-05** | Role Change Session Leak | High | Role changes updated PostgreSQL but did not revoke existing refresh sessions or increment `tokenVersion`; users maintained access tokens with previous roles. |
| **VULN-V16B-06** | Concurrency Race in Status Updates | High | `updateUserStatus` used non-locked `countByRoleAndEnabledTrue`, vulnerable to concurrent demotion/disablement races leaving zero active Super Admins. |
| **VULN-V16B-07** | Cache Inconsistency on Exception | Medium | `RedisSecurityStateProvider` evicted cache keys inside active transactions; if transactions rolled back, cache remained evicted or inconsistent. |

---

## 3. Technical Implementation Details

### 3.1 Pessimistic Write Locking (`UserRepository.java`)

To prevent race conditions where concurrent requests simultaneously delete, demote, or disable the last two active Super Admins, a dedicated pessimistic locking query with deterministic ordering was introduced:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM User u WHERE u.role = :role AND u.enabled = true ORDER BY u.id")
List<User> findActiveUsersByRoleForUpdate(@Param("role") Role role);
```

**Key Architectural Properties**:
- `LockModeType.PESSIMISTIC_WRITE` issues a native `SELECT ... FOR UPDATE` row lock in PostgreSQL.
- `ORDER BY u.id` guarantees that concurrent transactions acquire row locks in strictly identical order, eliminating cyclic wait deadlocks.

### 3.2 User Deletion Protection (`UserService.deleteUser`)

`UserService.java` implements dual security guards before executing user deletion:

```java
// Guard 1: Prevent self-deletion
if (authentication != null && authentication.getPrincipal() instanceof ProltUserPrincipal principal) {
    if (principal.getId().equals(user.getId()) || principal.getUsername().equalsIgnoreCase(user.getUsername())) {
        createAuditLog("DELETE_REJECTED", user.getId(), user.getUsername(), performedBy,
                Map.of("reason", "Self-deletion is prohibited"));
        throw new BadRequestException("You cannot delete your own account");
    }
}

// Guard 2: Prevent deletion of last active SUPER_ADMIN (concurrency-safe row locking)
final String targetId = id;
if (user.getRole() == Role.SUPER_ADMIN && user.isEnabled()) {
    List<User> activeSuperAdmins = userRepository.findActiveUsersByRoleForUpdate(Role.SUPER_ADMIN);
    long remainingActive = activeSuperAdmins.stream()
            .filter(u -> !u.getId().equals(targetId))
            .count();
    if (remainingActive == 0) {
        createAuditLog("DELETE_REJECTED", user.getId(), user.getUsername(), performedBy,
                Map.of("reason", "Cannot delete last active Super Administrator"));
        throw new BadRequestException("Cannot delete the last active Super Administrator account");
    }
}
```

**Session Cleanup Order**:
1. Active refresh sessions are revoked first: `refreshTokenService.revokeAllSessionsForUser(id);`
2. User row is deleted: `userRepository.deleteById(id); userRepository.flush();`
3. Security state cache is evicted: `securityStateProvider.evictSecurityState(id);`
4. Deletion audit log is created: `createAuditLog("DELETE", ...);`

### 3.3 Role & Module Privilege Update Handling (`UserService.updateUser`)

When a user's role or permissions are modified:
1. **Last Super Admin Demotion Guard**: Evaluates whether an active `SUPER_ADMIN` is being changed to another role (`ADMIN` or `CLIENT`). If remaining active count equals zero under pessimistic write lock, the demotion is rejected with `400 Bad Request` and `UPDATE_REJECTED` audit log.
2. **Selective `tokenVersion` Invalidation**:
   - Attribute change detection:
     - `role`: sets `authAttributesChanged = true`
     - `hiddenModules`: sets `authAttributesChanged = true`
     - `readOnlyModules`: sets `authAttributesChanged = true`
     - `fullName`, `email`: does **NOT** flag auth attribute changes.
3. **Session Revocation on Privilege Change**:
   ```java
   if (authAttributesChanged) {
       user.incrementTokenVersion();
       refreshTokenService.revokeAllSessionsForUser(user.getId());
       changes.put("tokenVersion", user.getTokenVersion());
   }
   user = userRepository.saveAndFlush(user);
   if (authAttributesChanged) {
       securityStateProvider.updateSecurityState(user.getId(), user.isEnabled(), user.getTokenVersion());
   }
   ```
4. **Profile Updates Retain Sessions**: When only `fullName` or `email` is updated, `tokenVersion` remains unchanged, no refresh sessions are revoked, and existing access tokens remain valid.

### 3.4 Concurrency-Hardened Account Disablement (`UserService.updateUserStatus`)

`updateUserStatus` was upgraded from unlocked count queries to pessimistic write locking:
- Uses `findActiveUsersByRoleForUpdate(Role.SUPER_ADMIN)` to lock all active Super Admins before counting.
- Ensures concurrent disabling requests cannot reduce active Super Admins below 1.
- Immediate `tokenVersion` increment and `revokeAllSessionsForUser` on disablement (`enabled = false`).

### 3.5 Transactional Audit Logging Integrity (`UserService.createAuditLog`)

To prevent security audit logs (`DELETE_REJECTED`, `UPDATE_REJECTED`) from being rolled back when an operational `BadRequestException` is thrown:

```java
private void createAuditLog(String action, String targetUserId, String targetUser,
                            String performedBy, Object changes) {
    try {
        UserAuditLog audit = UserAuditLog.create();
        audit.setAction(action);
        audit.setTargetUserId(targetUserId);
        audit.setTargetUser(targetUser);
        audit.setPerformedBy(performedBy != null ? performedBy : "SYSTEM");
        audit.setChanges(objectMapper.writeValueAsString(changes));
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tt.execute(status -> {
            userAuditLogRepository.save(audit);
            return null;
        });
    } catch (JsonProcessingException e) {
        log.error("Failed to serialize audit log changes: {}", e.getMessage());
    }
}
```

### 3.6 Redis Security State Cache Synchronization (`RedisSecurityStateProvider`)

1. **TTL Alignment**: Set `DEFAULT_TTL_SECONDS = 15L * 60` (15 minutes), exactly matching the V14 access token validity window.
2. **Transaction-Synchronized Eviction**: Cache eviction occurs only after the PostgreSQL transaction successfully commits:
   ```java
   if (TransactionSynchronizationManager.isActualTransactionActive()) {
       TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
           @Override
           public void afterCommit() {
               redisTemplate.delete(key);
           }
       });
   } else {
       redisTemplate.delete(key);
   }
   ```

---

## 4. Summary of Files Changed

| File | Change Category | Description |
|---|---|---|
| `src/main/java/com/sense/prolt_operations/repository/UserRepository.java` | Security / Concurrency | Added `@Lock(LockModeType.PESSIMISTIC_WRITE)` query `findActiveUsersByRoleForUpdate(Role)`. |
| `src/main/java/com/sense/prolt_operations/controller/UserController.java` | API Layer | Injected `Authentication` principal into `updateUser` and `deleteUser` calls. |
| `src/main/java/com/sense/prolt_operations/service/UserService.java` | Business Logic / Security | Implemented self-deletion guard, last Super Admin deletion/demotion guards, session revocation on privilege changes, `REQUIRES_NEW` audit logging. |
| `src/main/java/com/sense/prolt_operations/security/state/RedisSecurityStateProvider.java` | Cache Consistency | Updated TTL to 15m; synchronized cache eviction with database transaction commit. |
| `src/test/java/com/sense/prolt_operations/security/UserLifecyclePrivilegeHardeningIntegrationTest.java` | Automated Testing | Comprehensive test suite covering 12 lifecycle, privilege revocation, and concurrency test scenarios. |

---

## 5. Verification & Test Results

### 5.1 Targeted Integration Test Suite

Executed command:
```bash
./mvnw test -Dtest=UserLifecyclePrivilegeHardeningIntegrationTest
```

**Results**:
- **Tests run**: 12
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Time elapsed**: 16.61 s

| Test Identifier | Test Name | Result |
|---|---|---|
| `DEL-01` | Authenticated user cannot delete their own account (Self-Deletion Guard) | PASSED |
| `DEL-02` | Non-SuperAdmin (ADMIN) attempting to delete a user receives 403 Forbidden | PASSED |
| `DEL-03` | Deletion of the last active SUPER_ADMIN is rejected | PASSED |
| `DEL-04` | SUPER_ADMIN can delete an eligible client or admin user | PASSED |
| `DEL-05` | Disabled SUPER_ADMIN can be deleted when active SUPER_ADMIN exists | PASSED |
| `ROLE-01` | Authorized role update from CLIENT to ADMIN succeeds | PASSED |
| `ROLE-02` | Non-SuperAdmin attempting role update receives 403 Forbidden | PASSED |
| `ROLE-03` | Demoting the last active SUPER_ADMIN to ADMIN is rejected | PASSED |
| `ROLE-04` | Role update increments tokenVersion and revokes old access token and refresh sessions | PASSED |
| `MOD-01` | Module permission update (hiddenModules) increments tokenVersion and invalidates old token | PASSED |
| `MOD-02` | Non-authorization profile updates (fullName, email) preserve tokenVersion and active sessions | PASSED |
| `CONC-01` | Concurrent deletion attempts on last 2 active SUPER_ADMINs preserves at least 1 | PASSED |

### 5.2 Full Regression Suite

Executed command:
```bash
./mvnw test
```

**Results**:
- **Total Tests run**: 277
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 1 (`ScraperAndSchedulerIntegrationTest.testScraperEndToEndRealExecution` - external network scraper test)
- **Execution Time**: 1 min 13 s
- **Build Status**: **BUILD SUCCESS**

---

## 6. Safety & Constraint Adherence

1. **Local Database Untouched**: The local database (`localhost:5432/prolt_operations`) was completely untouched. Tests executed against isolated Docker Testcontainers.
2. **Flyway Migrations**: Zero new database migrations were added or executed. `spring.flyway.target=${FLYWAY_TARGET:8}` remains preserved.
3. **Frontend Untouched**: Zero frontend files were modified.
4. **Git Tree Clean**: No commits or git pushes were executed.
5. **Session Architecture Preserved**: V13 passwordless OTP authentication and V14 dual-token architecture (15m JWT access / 7d opaque refresh) remain fully intact.

---

## 7. Final Acceptance Checklist

- [x] Self-deletion is strictly prohibited with `400 Bad Request` and `DELETE_REJECTED` audit log.
- [x] Last active `SUPER_ADMIN` cannot be deleted, demoted, or disabled.
- [x] Concurrent privilege operations are protected by deterministic pessimistic row locking (`ORDER BY u.id`).
- [x] Role updates invalidate active JWT access tokens and revoke active refresh sessions.
- [x] Module permission updates (`hiddenModules`, `readOnlyModules`) invalidate active JWT access tokens.
- [x] Profile updates (`fullName`, `email`) preserve `tokenVersion` and user sessions.
- [x] Audit logs for rejected actions are committed via `Propagation.REQUIRES_NEW`.
- [x] Redis cache eviction is deferred to `afterCommit()`, with TTL aligned to 15 minutes.
- [x] All 277 automated tests pass with 0 regressions.
