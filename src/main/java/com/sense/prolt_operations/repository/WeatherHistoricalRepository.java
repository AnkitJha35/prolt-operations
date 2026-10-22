package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.WeatherHistorical;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface WeatherHistoricalRepository extends JpaRepository<WeatherHistorical, String>, JpaSpecificationExecutor<WeatherHistorical> {
}
