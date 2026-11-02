package com.sense.prolt_operations.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.sense.prolt_operations.entity.CtuCharges;

@Repository
public interface CtuChargesRepository extends JpaRepository<CtuCharges, String>, JpaSpecificationExecutor<CtuCharges> {
}
