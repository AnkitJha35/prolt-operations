package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.StateTariff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StateTariffRepository extends JpaRepository<StateTariff, Integer>, JpaSpecificationExecutor<StateTariff> {

    List<StateTariff> findByStateInAndConsumerCategoryIgnoreCaseAndSupplyVoltageCategoryIgnoreCase(
            List<String> states,
            String consumerCategory,
            String supplyVoltageCategory
    );

    List<StateTariff> findByStateIn(List<String> states);
}
