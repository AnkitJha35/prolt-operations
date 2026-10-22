package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.TraderPerformanceActualEntryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface TraderPerformanceActualEntryHistoryRepository extends JpaRepository<TraderPerformanceActualEntryHistory, String>, JpaSpecificationExecutor<TraderPerformanceActualEntryHistory> {
}
