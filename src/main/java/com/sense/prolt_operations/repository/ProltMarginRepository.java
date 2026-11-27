package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.ProltMargin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ProltMarginRepository extends JpaRepository<ProltMargin, Integer>, JpaSpecificationExecutor<ProltMargin> {
}
