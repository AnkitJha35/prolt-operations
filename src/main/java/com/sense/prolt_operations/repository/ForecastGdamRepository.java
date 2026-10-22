package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.forecasting.ForecastGdam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ForecastGdamRepository extends JpaRepository<ForecastGdam, String>, JpaSpecificationExecutor<ForecastGdam> {
}
