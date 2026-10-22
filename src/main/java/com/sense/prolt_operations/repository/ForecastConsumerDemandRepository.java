package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.forecasting.ForecastConsumerDemand;
import com.sense.prolt_operations.entity.forecasting.ForecastConsumerDemandId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ForecastConsumerDemandRepository extends JpaRepository<ForecastConsumerDemand, ForecastConsumerDemandId>, JpaSpecificationExecutor<ForecastConsumerDemand> {
}
