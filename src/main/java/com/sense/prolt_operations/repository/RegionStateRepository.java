package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.RegionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegionStateRepository extends JpaRepository<RegionState, Integer>, JpaSpecificationExecutor<RegionState> {

    Optional<RegionState> findFirstByStateCodeIgnoreCase(String stateCode);

    Optional<RegionState> findFirstByStateNameIgnoreCase(String stateName);

    Optional<RegionState> findFirstByStateCodeIgnoreCaseOrStateNameIgnoreCase(String stateCode, String stateName);
}
