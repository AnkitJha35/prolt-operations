package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.forecasting.ForecastAllIndiaDemand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ForecastAllIndiaDemandRepository extends JpaRepository<ForecastAllIndiaDemand, String>, JpaSpecificationExecutor<ForecastAllIndiaDemand> {
}
