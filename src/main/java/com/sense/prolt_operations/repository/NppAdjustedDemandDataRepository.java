package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.NppAdjustedDemandData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface NppAdjustedDemandDataRepository extends JpaRepository<NppAdjustedDemandData, String>, JpaSpecificationExecutor<NppAdjustedDemandData> {
}
