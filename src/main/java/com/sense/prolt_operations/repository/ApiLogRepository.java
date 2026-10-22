package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.ApiLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiLogRepository extends JpaRepository<ApiLog, String>, JpaSpecificationExecutor<ApiLog> {
}
