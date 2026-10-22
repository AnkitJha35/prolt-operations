package com.sense.prolt_operations.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sense.prolt_operations.entity.ApiLog;
import com.sense.prolt_operations.repository.ApiLogRepository;

@Service
@Transactional
public class ApiLogService {

    private static final Logger log = LoggerFactory.getLogger(ApiLogService.class);

    private final ApiLogRepository apiLogRepository;

    public ApiLogService(ApiLogRepository apiLogRepository) {
        this.apiLogRepository = apiLogRepository;
    }

    public ApiLog createLog(String apiName, String endpoint, String status, String details) {
        try {
            ApiLog entry = ApiLog.create();
            entry.setApiName(apiName);
            entry.setEndpoint(endpoint);
            entry.setStatus(status);
            entry.setDetails(details);
            return apiLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to persist ApiLog: {}", e.getMessage());
            return null;
        }
    }
}
