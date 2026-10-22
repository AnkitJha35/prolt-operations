package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.CtuCharges;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CtuChargesRepository extends JpaRepository<CtuCharges, String>, JpaSpecificationExecutor<CtuCharges> {
}
