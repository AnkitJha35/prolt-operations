package com.sense.prolt_operations.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.sense.prolt_operations.BaseIntegrationTest;
import com.sense.prolt_operations.entity.DamRecord;
import com.sense.prolt_operations.entity.Dataset;
import com.sense.prolt_operations.entity.DatasetStatus;
import com.sense.prolt_operations.entity.MarketType;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.entity.User;

public class RepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DamRecordRepository damRecordRepository;

    @Autowired
    private SavingsCalculatorEntryRepository savingsCalculatorEntryRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    void testFreshFlywayMigrationLeavesZeroRowsInUserTable() {
        String freshDbName = "fresh_flyway_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try (java.sql.Connection adminConn = java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             java.sql.Statement adminStmt = adminConn.createStatement()) {
            adminStmt.execute("CREATE DATABASE \"" + freshDbName + "\"");
        } catch (java.sql.SQLException e) {
            fail("Failed to create temporary fresh database: " + e.getMessage());
        }

        String freshDbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + freshDbName);
        try {
            org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                    .dataSource(freshDbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();

            try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
                    freshDbUrl, postgres.getUsername(), postgres.getPassword());
                 java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM \"public\".\"User\"")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getLong(1), "Fresh Flyway migration from scratch must leave exactly zero rows in User");
            }
        } catch (Exception e) {
            fail("Failed to verify User table count in fresh Flyway migration: " + e.getMessage());
        } finally {
            try (java.sql.Connection adminConn = java.sql.DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 java.sql.Statement adminStmt = adminConn.createStatement()) {
                adminStmt.execute("DROP DATABASE IF EXISTS \"" + freshDbName + "\" WITH (FORCE)");
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void testUserCrudOperations() {
        String testUsername = "test_user_" + UUID.randomUUID().toString().substring(0, 8);
        User user = User.create();
        user.setUsername(testUsername);
        user.setEmail(testUsername + "@probus.io");
        user.setPasswordHash(passwordEncoder.encode("TestCrudUserPass123!"));
        user.setRole(Role.CLIENT);
        user.setFullName("Test CRUD User");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        assertNotNull(saved.getId(), "Generated UUID id must not be null");

        Optional<User> found = userRepository.findByUsername(testUsername);
        assertTrue(found.isPresent());
        assertEquals(Role.CLIENT, found.get().getRole());

        userRepository.delete(saved);
        assertTrue(userRepository.findByUsername(testUsername).isEmpty());
    }

    @Test
    void testDatasetAndDamRecordPersistence() {
        Dataset dataset = Dataset.create();
        dataset.setMarket(MarketType.DAM);
        dataset.setDeliveryDate(LocalDate.of(2026, 6, 20));
        dataset.setFileName("test_dam_file.xlsx");
        dataset.setStatus(DatasetStatus.ACTIVE);
        dataset.setUploadedAt(LocalDateTime.now());
        dataset.setCreatedAt(LocalDateTime.now());
        dataset.setUpdatedAt(LocalDateTime.now());

        Dataset savedDataset = datasetRepository.save(dataset);
        assertNotNull(savedDataset.getId(), "Generated dataset UUID must not be null");

        DamRecord damRecord = DamRecord.create();
        damRecord.setDatasetId(savedDataset.getId());
        damRecord.setIntervalNumber((short) 1);
        damRecord.setIntervalTime("00:00 - 00:15");
        damRecord.setPurchaseBid(new BigDecimal("5000.0000"));
        damRecord.setSellBid(new BigDecimal("4800.0000"));
        damRecord.setMcv(new BigDecimal("4200.0000"));
        damRecord.setFsv(new BigDecimal("4200.0000"));
        damRecord.setMcp(new BigDecimal("3500.0000"));

        DamRecord savedRecord = damRecordRepository.save(damRecord);
        assertNotNull(savedRecord.getId(), "Generated damRecord UUID must not be null");
        assertEquals(new BigDecimal("3500.0000"), savedRecord.getMcp());

        damRecordRepository.delete(savedRecord);
        datasetRepository.delete(savedDataset);
    }

    @Test
    void testSavingsCalculatorEntryPersistence() {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setClientName("Acme Steel Mills");
        entry.setIndustryName("Steel Manufacturing");
        entry.setAddress("Industrial Area, Phase 2");
        entry.setStateCode("UP");
        entry.setDiscom("UPPCL");
        entry.setConsumerCategory("HV-2");
        entry.setVoltageLevel("33kV");
        entry.setApplyElectricityDuty(true);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());

        SavingsCalculatorEntry saved = savingsCalculatorEntryRepository.save(entry);
        assertNotNull(saved.getId(), "Generated entry UUID must not be null");

        Optional<SavingsCalculatorEntry> found = savingsCalculatorEntryRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Acme Steel Mills", found.get().getClientName());

        savingsCalculatorEntryRepository.delete(saved);
    }
}
