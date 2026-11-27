package com.sense.prolt_operations.security.state;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.repository.UserSecurityState;

public class DatabaseSecurityStateProvider implements UserSecurityStateProvider {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSecurityStateProvider.class);

    private final UserRepository userRepository;

    public DatabaseSecurityStateProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UserSecurityState> getSecurityState(String userId) {
        log.trace("Reading user security state directly from PostgreSQL for userId='{}'", userId);
        return userRepository.findSecurityStateById(userId);
    }

    @Override
    public void updateSecurityState(String userId, boolean enabled, int tokenVersion) {
        // No-op: PostgreSQL is already modified synchronously in the service layer
        log.trace("Database security state provider updateSecurityState is a no-op for userId='{}'", userId);
    }

    @Override
    public void evictSecurityState(String userId) {
        // No-op: No cache in database-only mode
        log.trace("Database security state provider evictSecurityState is a no-op for userId='{}'", userId);
    }
}
