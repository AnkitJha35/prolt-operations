package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, String>, JpaSpecificationExecutor<Dataset> {
}
