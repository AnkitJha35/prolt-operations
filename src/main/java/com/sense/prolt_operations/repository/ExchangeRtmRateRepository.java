package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.ExchangeRtmRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ExchangeRtmRateRepository extends JpaRepository<ExchangeRtmRate, Long>, JpaSpecificationExecutor<ExchangeRtmRate> {
}
