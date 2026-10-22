package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.PublicGdammcpforecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PublicGdammcpforecastRepository extends JpaRepository<PublicGdammcpforecast, String>, JpaSpecificationExecutor<PublicGdammcpforecast> {
}
