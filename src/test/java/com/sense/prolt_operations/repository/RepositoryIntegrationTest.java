package com.sense.prolt_operations.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void testFlywayBaselineAndSeededUsers() {
        Optional<User> admin = userRepository.findByUsername("admin");
        assertTrue(admin.isPresent(), "Admin user must be present from Flyway seed");
        assertEquals(Role.ADMIN, admin.get().getRole());

        Optional<User> superadmin = userRepository.findByUsername("superadmin");
        assertTrue(superadmin.isPresent(), "Superadmin user must be present from Flyway seed");
        assertEquals(Role.SUPER_ADMIN, superadmin.get().getRole());
        assertEquals("super.admin@probus.io", superadmin.get().getEmail());
    }

    @Test
    void testUserCrudOperations() {
        String testUsername = "test_user_" + UUID.randomUUID().toString().substring(0, 8);
        User user = User.create();
        user.setUsername(testUsername);
        user.setEmail(testUsername + "@probus.io");
        user.setPasswordHash("$2a$10$dummyHashValueForTestingOnly");
        user.setRole(Role.CLIENT);
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
