package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.SavingsCalculatorNewEntryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavingsCalculatorNewEntryHistoryRepository extends JpaRepository<SavingsCalculatorNewEntryHistory, String>, JpaSpecificationExecutor<SavingsCalculatorNewEntryHistory> {
    List<SavingsCalculatorNewEntryHistory> findByEntryIdOrderByVersionDesc(String entryId);
    Optional<SavingsCalculatorNewEntryHistory> findByEntryIdAndVersion(String entryId, Integer version);
    void deleteByEntryId(String entryId);
}
