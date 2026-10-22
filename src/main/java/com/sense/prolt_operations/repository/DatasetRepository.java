package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DatasetRepository extends JpaRepository<Dataset, String>, JpaSpecificationExecutor<Dataset> {

    java.util.List<Dataset> findByMarketAndDeliveryDateAndStatus(
            com.sense.prolt_operations.entity.MarketType market,
            java.time.LocalDate deliveryDate,
            com.sense.prolt_operations.entity.DatasetStatus status);

    java.util.Optional<Dataset> findFirstByMarketAndDeliveryDateAndStatus(
            com.sense.prolt_operations.entity.MarketType market,
            java.time.LocalDate deliveryDate,
            com.sense.prolt_operations.entity.DatasetStatus status);

    java.util.List<Dataset> findByMarketAndStatusAndDeliveryDateBetweenOrderByDeliveryDateAsc(
            com.sense.prolt_operations.entity.MarketType market,
            com.sense.prolt_operations.entity.DatasetStatus status,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate);

    long countByStatus(com.sense.prolt_operations.entity.DatasetStatus status);

    java.util.List<Dataset> findByStatus(com.sense.prolt_operations.entity.DatasetStatus status);
}
