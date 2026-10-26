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
import org.springframework.security.crypto.password.PasswordEncoder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppRunnerTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private MockEnvironment environment;
    private AppRunner appRunner;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger appRunnerLogger;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        environment = new MockEnvironment();
        appRunner = new AppRunner(userRepository, passwordEncoder, environment);

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

    private void assertNoPasswordInLogs(String... sensitiveStrings) {
        for (ILoggingEvent event : listAppender.list) {
            String message = event.getFormattedMessage();
            for (String sensitive : sensitiveStrings) {
                if (sensitive != null && !sensitive.isBlank()) {
                    assertThat(message)
                        .withFailMessage("Log message contained sensitive password data!")
                        .doesNotContain(sensitive);
                }
            }
        }
    }

    @Test
    void createsAdminWhenMissingWithRoleAdmin() throws Exception {
        String username = "testadmin";
        String email = "testadmin@probus.io";
        String testPassword = "AdminTestPassword123!";

        appRunner.setAdminUsername(username);
        appRunner.setAdminEmail(email);
        appRunner.setAdminPassword(testPassword);

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(testPassword)).thenReturn("encoded_admin_pass_stub");

        appRunner.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User created = captor.getValue();
        assertThat(created.getUsername()).isEqualTo(username);
        assertThat(created.getEmail()).isEqualTo(email);
        assertThat(created.getRole()).isEqualTo(Role.ADMIN);
        assertThat(created.getPasswordHash()).isEqualTo("encoded_admin_pass_stub");

        assertThat(listAppender.list)
            .anyMatch(event -> event.getLevel() == Level.INFO && event.getFormattedMessage().contains("Created default admin user"));

        assertNoPasswordInLogs(testPassword);
    }

    @Test
    void leavesExistingAdminUntouchedAndLogsAlreadyExists() throws Exception {
        String username = "existingadmin";
        String email = "existing@probus.io";
        String testPassword = "AdminTestPassword123!";

        appRunner.setAdminUsername(username);
        appRunner.setAdminEmail(email);
        appRunner.setAdminPassword(testPassword);

        User existing = User.create();
        existing.setId("existing-id-123");
        existing.setUsername(username);
        existing.setEmail("original@probus.io");
        existing.setPasswordHash("original_preserved_hash");
        existing.setRole(Role.ADMIN);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(existing));

        appRunner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any(User.class));
        assertThat(existing.getEmail()).isEqualTo("original@probus.io");
        assertThat(existing.getPasswordHash()).isEqualTo("original_preserved_hash");
        assertThat(existing.getRole()).isEqualTo(Role.ADMIN);

        assertThat(listAppender.list)
            .anyMatch(event -> event.getLevel() == Level.INFO && event.getFormattedMessage().contains("already exists"));

        assertNoPasswordInLogs(testPassword);
    }

    @Test
    void refusesShortPasswordUnder12CharactersInNonProd() throws Exception {
        String username = "adminuser";
        String email = "adminuser@probus.io";
        String shortPassword = "ShortPass1!"; // 11 chars

        appRunner.setAdminUsername(username);
        appRunner.setAdminEmail(email);
        appRunner.setAdminPassword(shortPassword);

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        appRunner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());

        assertThat(listAppender.list)
            .anyMatch(event -> event.getLevel() == Level.ERROR && event.getFormattedMessage().contains("password policy violation"));

        assertNoPasswordInLogs(shortPassword);
    }

    @Test
    void refusesPasswordEqualToUsernameInNonProd() throws Exception {
        String username = "equal_username_123";
        String email = "equal@probus.io";
        String equalPassword = "equal_username_123";

        appRunner.setAdminUsername(username);
        appRunner.setAdminEmail(email);
        appRunner.setAdminPassword(equalPassword);

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        appRunner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());

        assertThat(listAppender.list)
            .anyMatch(event -> event.getLevel() == Level.ERROR && event.getFormattedMessage().contains("password policy violation"));

        assertNoPasswordInLogs(equalPassword);
    }

    @Test
    void underProdProfileShortPasswordRefusalThrowsException() {
        environment.setActiveProfiles("prod");

        String username = "prodadmin";
        String email = "prodadmin@probus.io";
        String shortPassword = "short";

        appRunner.setAdminUsername(username);
        appRunner.setAdminEmail(email);
        appRunner.setAdminPassword(shortPassword);

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appRunner.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Admin user password policy violation in production profile");

        verify(userRepository, never()).save(any(User.class));

        assertThat(listAppender.list)
            .anyMatch(event -> event.getLevel() == Level.ERROR && event.getFormattedMessage().contains("password policy violation"));

        assertNoPasswordInLogs(shortPassword);
    }

    @Test
    void underProdProfilePasswordEqualUsernameThrowsException() {
        environment.setActiveProfiles("prod");

        String username = "production_admin_user";
        String email = "prodadmin@probus.io";
        String equalPassword = "production_admin_user";

        appRunner.setAdminUsername(username);
        appRunner.setAdminEmail(email);
        appRunner.setAdminPassword(equalPassword);

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appRunner.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Admin user password policy violation in production profile");

        verify(userRepository, never()).save(any(User.class));

        assertThat(listAppender.list)
            .anyMatch(event -> event.getLevel() == Level.ERROR && event.getFormattedMessage().contains("password policy violation"));

        assertNoPasswordInLogs(equalPassword);
    }

    @Test
    void allThreeBlankLogsInfoAndCreatesNothing() throws Exception {
        appRunner.setAdminUsername("");
        appRunner.setAdminEmail("");
        appRunner.setAdminPassword("");

        appRunner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).findByUsername(anyString());
        verify(userRepository, never()).save(any(User.class));

        assertThat(listAppender.list)
            .anyMatch(event -> event.getLevel() == Level.INFO && event.getFormattedMessage().contains("skipped"));
    }

    @Test
    void partiallyBlankLogsInfoAndCreatesNothing() throws Exception {
        appRunner.setAdminUsername("admin");
        appRunner.setAdminEmail("");
        appRunner.setAdminPassword("AdminTestPassword123!");

        appRunner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).findByUsername(anyString());
        verify(userRepository, never()).save(any(User.class));

        assertThat(listAppender.list)
            .anyMatch(event -> event.getLevel() == Level.INFO && event.getFormattedMessage().contains("skipped"));
    }
}
