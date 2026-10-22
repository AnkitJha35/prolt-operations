package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.NppRawDemandData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface NppRawDemandDataRepository extends JpaRepository<NppRawDemandData, String>, JpaSpecificationExecutor<NppRawDemandData> {
}
