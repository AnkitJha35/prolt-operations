package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.StateCharges;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StateChargesRepository extends JpaRepository<StateCharges, Integer>, JpaSpecificationExecutor<StateCharges> {

    List<StateCharges> findByStateInAndCategoryIgnoreCase(
            List<String> states,
            String category
    );

    List<StateCharges> findByStateIn(List<String> states);
}
