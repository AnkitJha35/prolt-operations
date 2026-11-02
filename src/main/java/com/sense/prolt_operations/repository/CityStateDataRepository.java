package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.CityStateData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface CityStateDataRepository extends JpaRepository<CityStateData, String>, JpaSpecificationExecutor<CityStateData> {
    List<CityStateData> findAllByOrderByStateNameAscCityNameAsc();
    boolean existsByCityNameAndStateName(String cityName, String stateName);
}
