package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.TraderPerformanceEntryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TraderPerformanceEntryHistoryRepository extends JpaRepository<TraderPerformanceEntryHistory, String>, JpaSpecificationExecutor<TraderPerformanceEntryHistory> {
    List<TraderPerformanceEntryHistory> findByEntryIdOrderByVersionDesc(String entryId);
    Optional<TraderPerformanceEntryHistory> findByEntryIdAndVersion(String entryId, Integer version);
    void deleteByEntryId(String entryId);
}
