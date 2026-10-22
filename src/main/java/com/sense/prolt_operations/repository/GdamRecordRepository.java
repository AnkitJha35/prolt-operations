package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.GdamRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface GdamRecordRepository extends JpaRepository<GdamRecord, String>, JpaSpecificationExecutor<GdamRecord> {

    java.util.List<GdamRecord> findByDatasetIdOrderByIntervalNumberAsc(String datasetId);

    void deleteByDatasetId(String datasetId);
}
