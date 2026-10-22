package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.forecasting.DamForecasting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DamForecastingRepository extends JpaRepository<DamForecasting, String>, JpaSpecificationExecutor<DamForecasting> {
}
