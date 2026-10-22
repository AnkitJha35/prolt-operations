package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.FppaCharges;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface FppaChargesRepository extends JpaRepository<FppaCharges, String>, JpaSpecificationExecutor<FppaCharges> {
}
