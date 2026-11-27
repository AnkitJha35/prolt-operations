package com.sense.prolt_operations.runner;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

class AppRunnerTest {

    private UserRepository userRepository;
    private MockEnvironment environment;
    private AppRunner appRunner;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger appRunnerLogger;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        environment = new MockEnvironment();
        appRunner = new AppRunner(userRepository, environment);

        appRunnerLogger = (Logger) LoggerFactory.getLogger(AppRunner.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        appRunnerLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        if (appRunnerLogger != null && listAppender != null) {
            appRunnerLogger.detachAppender(listAppender);
        }
    }

    @Test
    void createsAdminWhenMissingWithRoleAdmin() throws Exception {
        String username = "testadmin";
        String email = "testadmin@probus.io";

        appRunner.setAdminUsername(username);
        appRunner.setAdminEmail(email);

        when(userRepository.findByNormalizedUsername(username)).thenReturn(Optional.empty());
        when(userRepository.findByNormalizedEmail(email)).thenReturn(Optional.empty());

        appRunner.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User created = captor.getValue();
        assertThat(created.getUsername()).isEqualTo(username);
        assertThat(created.getEmail()).isEqualTo(email);
        assertThat(created.getRole()).isEqualTo(Role.ADMIN);
        assertThat(created.getFullName()).isEqualTo("System Administrator");

        assertThat(listAppender.list)
            .anyMatch(event -> event.getLevel() == Level.INFO && event.getFormattedMessage().contains("Created default admin user"));
    }

    @Test
    void leavesExistingAdminUntouchedAndLogsAlreadyExists() throws Exception {
        String username = "existingadmin";
        String email = "existing@probus.io";

        appRunner.setAdminUsername(username);
        appRunner.setAdminEmail(email);

        User existing = User.create();
        existing.setId("existing-id-123");
        existing.setUsername(username);
        existing.setEmail("existing@probus.io");
        existing.setRole(Role.ADMIN);

        when(userRepository.findByNormalizedUsername(username)).thenReturn(Optional.of(existing));

        appRunner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any(User.class));
        assertThat(existing.getEmail()).isEqualTo("existing@probus.io");
        assertThat(existing.getRole()).isEqualTo(Role.ADMIN);

        assertThat(listAppender.list)
            .anyMatch(event -> event.getLevel() == Level.INFO && event.getFormattedMessage().contains("already exists"));
    }

    @Test
    void allBlankLogsInfoAndCreatesNothing() throws Exception {
        appRunner.setAdminUsername("");
        appRunner.setAdminEmail("");

        appRunner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).findByNormalizedUsername(anyString());
        verify(userRepository, never()).save(any(User.class));

        assertThat(listAppender.list)
            .anyMatch(event -> event.getLevel() == Level.INFO && event.getFormattedMessage().contains("skipped"));
    }

    @Test
    void partiallyBlankLogsInfoAndCreatesNothing() throws Exception {
        appRunner.setAdminUsername("admin");
        appRunner.setAdminEmail("");

        appRunner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).findByNormalizedUsername(anyString());
        verify(userRepository, never()).save(any(User.class));

        assertThat(listAppender.list)
            .anyMatch(event -> event.getLevel() == Level.INFO && event.getFormattedMessage().contains("skipped"));
    }
}
