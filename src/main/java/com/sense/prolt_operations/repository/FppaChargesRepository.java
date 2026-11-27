package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.FppaCharges;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FppaChargesRepository extends JpaRepository<FppaCharges, Integer>, JpaSpecificationExecutor<FppaCharges> {

    List<FppaCharges> findByStateInAndMonth(List<String> states, Integer month);

    List<FppaCharges> findByStateInOrderByMonthDesc(List<String> states);
}
