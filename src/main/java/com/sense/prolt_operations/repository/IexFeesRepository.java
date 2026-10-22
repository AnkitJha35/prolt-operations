package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.IexFees;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface IexFeesRepository extends JpaRepository<IexFees, String>, JpaSpecificationExecutor<IexFees> {
}
