package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.dto.calculator.ExchangeRateSlotProjection;
import com.sense.prolt_operations.entity.ExchangeDamRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExchangeDamRateRepository extends JpaRepository<ExchangeDamRate, Long>, JpaSpecificationExecutor<ExchangeDamRate> {

    @Query(value = """
        SELECT
            COALESCE(dam.date, rtm.date, gdam.date) AS date,
            COALESCE(dam.timeblock, rtm.timeblock, gdam.timeblock) AS timeblock,
            dam.mcp AS damMcp,
            rtm.mcp AS rtmMcp,
            gdam.mcp AS gdamMcp
        FROM
            (SELECT date, CAST((EXTRACT(HOUR FROM "intervalTime") * 60 + EXTRACT(MINUTE FROM "intervalTime")) / 15 + 1 AS INTEGER) AS timeblock, mcp
             FROM exchange_dam_rate
             WHERE state = :state AND date >= :startDate AND date <= :endDate) dam
        FULL OUTER JOIN
            (SELECT date, CAST((EXTRACT(HOUR FROM "intervalTime") * 60 + EXTRACT(MINUTE FROM "intervalTime")) / 15 + 1 AS INTEGER) AS timeblock, mcp
             FROM exchange_rtm_rate
             WHERE state = :state AND date >= :startDate AND date <= :endDate) rtm
            ON dam.date = rtm.date AND dam.timeblock = rtm.timeblock
        FULL OUTER JOIN
            (SELECT date, CAST((EXTRACT(HOUR FROM "intervalTime") * 60 + EXTRACT(MINUTE FROM "intervalTime")) / 15 + 1 AS INTEGER) AS timeblock, mcp
             FROM exchange_gdam_rate
             WHERE state = :state AND date >= :startDate AND date <= :endDate) gdam
            ON COALESCE(dam.date, rtm.date) = gdam.date AND COALESCE(dam.timeblock, rtm.timeblock) = gdam.timeblock
        ORDER BY date ASC, timeblock ASC
        """, nativeQuery = true)
    List<ExchangeRateSlotProjection> findCombinedExchangeRates(
            @Param("state") String state,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
