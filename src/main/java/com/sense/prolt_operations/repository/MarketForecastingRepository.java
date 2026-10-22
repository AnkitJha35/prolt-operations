package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.forecasting.MarketForecasting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketForecastingRepository extends JpaRepository<MarketForecasting, String>, JpaSpecificationExecutor<MarketForecasting> {
}
