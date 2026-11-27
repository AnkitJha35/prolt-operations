package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.IstsCharges;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface IstsChargesRepository extends JpaRepository<IstsCharges, Integer>, JpaSpecificationExecutor<IstsCharges> {

    @Query("SELECT i FROM IstsCharges i WHERE i.startDate <= :endDate AND i.endDate >= :startDate ORDER BY i.startDate ASC")
    List<IstsCharges> findActiveChargesBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
