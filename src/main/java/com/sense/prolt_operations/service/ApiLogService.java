package com.sense.prolt_operations.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
            entry.setEndpoint(endpoint != null && endpoint.length() > 255 ? endpoint.substring(0, 255) : endpoint);
            entry.setStatus(status);
            entry.setDetails(details);
            entry.setCreatedAt(LocalDateTime.now());
            return apiLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to persist ApiLog: {}", e.getMessage());
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLogs(int page, int limit, String startDate, String endDate, String apiName) {
        int pageIndex = Math.max(0, page - 1);
        int pageSize = limit > 0 ? limit : 100;

        Specification<ApiLog> spec = buildSpec(startDate, endDate, apiName);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ApiLog> paged = apiLogRepository.findAll(spec, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("data", paged.getContent());
        result.put("total", paged.getTotalElements());
        return result;
    }

    @Transactional(readOnly = true)
    public List<String> getUniqueApiNames() {
        return apiLogRepository.findAll().stream()
                .map(ApiLog::getApiName)
                .filter(name -> name != null && !name.trim().isEmpty())
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats(String startDate, String endDate, String apiName) {
        Specification<ApiLog> spec = buildSpec(startDate, endDate, apiName);
        List<ApiLog> logs = apiLogRepository.findAll(spec);

        long total = logs.size();
        long success = logs.stream().filter(l -> "SUCCESS".equalsIgnoreCase(l.getStatus())).count();
        long error = total - success;
        double accuracy = total > 0 ? ((double) success / total) * 100.0 : 100.0;

        return Map.of(
            "total", total,
            "success", success,
            "error", error,
            "accuracy", accuracy
        );
    }

    private Specification<ApiLog> buildSpec(String startDate, String endDate, String apiName) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();
            if (startDate != null && !startDate.trim().isEmpty()) {
                LocalDateTime start = LocalDate.parse(startDate.trim()).atStartOfDay();
                predicates = cb.and(predicates, cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (endDate != null && !endDate.trim().isEmpty()) {
                LocalDateTime end = LocalDate.parse(endDate.trim()).atTime(LocalTime.MAX);
                predicates = cb.and(predicates, cb.lessThanOrEqualTo(root.get("createdAt"), end));
            }
            if (apiName != null && !apiName.trim().isEmpty()) {
                predicates = cb.and(predicates, cb.equal(root.get("apiName"), apiName.trim()));
            }
            return predicates;
        };
    }
}
