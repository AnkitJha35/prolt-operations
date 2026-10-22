package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.forecasting.RtmDayahead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RtmDayaheadRepository extends JpaRepository<RtmDayahead, String>, JpaSpecificationExecutor<RtmDayahead> {
}
