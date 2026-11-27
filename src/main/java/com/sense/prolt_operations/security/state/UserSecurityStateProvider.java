package com.sense.prolt_operations.security.state;

import java.util.Optional;
import com.sense.prolt_operations.repository.UserSecurityState;

public interface UserSecurityStateProvider {

    /**
     * Retrieves the current security state (enabled status and tokenVersion) for the given user ID.
     *
     * @param userId the user's unique ID
     * @return an Optional containing the security state if the user exists, or empty if not found
     * @throws com.sense.prolt_operations.exception.SecurityStateUnavailableException if the provider fails closed
     */
    Optional<UserSecurityState> getSecurityState(String userId);

    /**
     * Updates the cached security state for the user with atomic version awareness.
     * In the database provider, this is a no-op as the database is the direct source of truth.
     *
     * @param userId the user's unique ID
     * @param enabled whether the account is enabled
     * @param tokenVersion the new token version
     */
    void updateSecurityState(String userId, boolean enabled, int tokenVersion);

    /**
     * Evicts any cached security state for the user.
     *
     * @param userId the user's unique ID
     */
    void evictSecurityState(String userId);
}
