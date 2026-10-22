package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.DsmCharges;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DsmChargesRepository extends JpaRepository<DsmCharges, String>, JpaSpecificationExecutor<DsmCharges> {
}
