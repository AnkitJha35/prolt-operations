package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.HistoricalWeather;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface HistoricalWeatherRepository extends JpaRepository<HistoricalWeather, String>, JpaSpecificationExecutor<HistoricalWeather> {
}
