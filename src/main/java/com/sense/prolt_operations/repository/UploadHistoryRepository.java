package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.UploadHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadHistoryRepository extends JpaRepository<UploadHistory, String>, JpaSpecificationExecutor<UploadHistory> {

    java.util.List<UploadHistory> findByDatasetIdOrderByTimestampAsc(String datasetId);

    java.util.Optional<UploadHistory> findFirstByOrderByTimestampDesc();
}
