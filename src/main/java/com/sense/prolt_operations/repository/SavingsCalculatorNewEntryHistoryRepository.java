package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.SavingsCalculatorNewEntryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SavingsCalculatorNewEntryHistoryRepository extends JpaRepository<SavingsCalculatorNewEntryHistory, String>, JpaSpecificationExecutor<SavingsCalculatorNewEntryHistory> {
}
