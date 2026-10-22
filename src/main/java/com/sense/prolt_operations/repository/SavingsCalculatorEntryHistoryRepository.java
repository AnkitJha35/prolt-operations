package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.SavingsCalculatorEntryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavingsCalculatorEntryHistoryRepository extends JpaRepository<SavingsCalculatorEntryHistory, String>, JpaSpecificationExecutor<SavingsCalculatorEntryHistory> {
    List<SavingsCalculatorEntryHistory> findByEntryIdOrderByVersionDesc(String entryId);
    Optional<SavingsCalculatorEntryHistory> findByEntryIdAndVersion(String entryId, Integer version);
    void deleteByEntryId(String entryId);
}
