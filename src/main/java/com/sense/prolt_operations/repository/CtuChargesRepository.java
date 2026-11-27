package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.CtuCharges;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CtuChargesRepository extends JpaRepository<CtuCharges, Integer>, JpaSpecificationExecutor<CtuCharges> {

    Optional<CtuCharges> findFirstByMonth(Integer month);

    Optional<CtuCharges> findFirstByStateInAndMonth(List<String> states, Integer month);

    Optional<CtuCharges> findFirstByOrderByMonthDesc();
}
