package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.DamRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DamRecordRepository extends JpaRepository<DamRecord, String>, JpaSpecificationExecutor<DamRecord> {

    java.util.List<DamRecord> findByDatasetIdOrderByIntervalNumberAsc(String datasetId);

    void deleteByDatasetId(String datasetId);
}
