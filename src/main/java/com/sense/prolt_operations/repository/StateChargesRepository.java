package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.StateCharges;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface StateChargesRepository extends JpaRepository<StateCharges, String>, JpaSpecificationExecutor<StateCharges> {
}
