package com.sense.prolt_operations.repository;

import java.time.LocalDate;
import java.util.Optional;
import com.sense.prolt_operations.entity.MarketType;
import com.sense.prolt_operations.entity.StateMarketRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface StateMarketRecordRepository extends JpaRepository<StateMarketRecord, String>, JpaSpecificationExecutor<StateMarketRecord> {
    Optional<StateMarketRecord> findByMarketAndAreaAndDeliveryDateAndIntervalNumber(
            MarketType market, String area, LocalDate deliveryDate, Short intervalNumber);
}
