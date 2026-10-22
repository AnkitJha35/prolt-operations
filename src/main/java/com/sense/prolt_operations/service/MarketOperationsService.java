package com.sense.prolt_operations.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@Service
@Transactional(readOnly = true)
public class MarketOperationsService {

    private static final Logger log = LoggerFactory.getLogger(MarketOperationsService.class);

    private final EntityManager entityManager;

    public MarketOperationsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRecords(String startDate, String endDate) {
        StringBuilder dateFilter = new StringBuilder();
        if (startDate != null && !startDate.trim().isEmpty() && endDate != null && !endDate.trim().isEmpty()) {
            dateFilter.append(" AND d.\"deliveryDate\" >= :startDate AND d.\"deliveryDate\" <= :endDate ");
        } else if (startDate != null && !startDate.trim().isEmpty()) {
            dateFilter.append(" AND d.\"deliveryDate\" >= :startDate ");
        } else if (endDate != null && !endDate.trim().isEmpty()) {
            dateFilter.append(" AND d.\"deliveryDate\" <= :endDate ");
        }

        String filter = dateFilter.toString();
        String sql = """
            SELECT
                COALESCE(dam.date, rtm.date, gdam.date) AS date,
                COALESCE(dam.timeblock, rtm.timeblock, gdam.timeblock) AS timeblock,
                dam.mcp AS damMcp,
                rtm.mcp AS rtmMcp,
                gdam.mcp AS gdamMcp
            FROM
                (SELECT d."deliveryDate" as date, dr."intervalNumber" as timeblock, dr.mcp\s
                 FROM "DamRecord" dr\s
                 JOIN "Dataset" d ON dr."datasetId" = d.id\s
                 WHERE d.market = 'DAM' AND d.status = 'ACTIVE' %s) dam
            FULL OUTER JOIN
                (SELECT d."deliveryDate" as date, rr."intervalNumber" as timeblock, rr.mcp\s
                 FROM "RtmRecord" rr\s
                 JOIN "Dataset" d ON rr."datasetId" = d.id\s
                 WHERE d.market = 'RTM' AND d.status = 'ACTIVE' %s) rtm
                ON dam.date = rtm.date AND dam.timeblock = rtm.timeblock
            FULL OUTER JOIN
                (SELECT d."deliveryDate" as date, gr."intervalNumber" as timeblock, gr.mcp\s
                 FROM "GdamRecord" gr\s
                 JOIN "Dataset" d ON gr."datasetId" = d.id\s
                 WHERE d.market = 'GDAM' AND d.status = 'ACTIVE' %s
                 UNION ALL
                 SELECT d."deliveryDate" as date, gnr."intervalNumber" as timeblock, gnr.mcp\s
                 FROM "GdamNewRecord" gnr\s
                 JOIN "Dataset" d ON gnr."datasetId" = d.id\s
                 WHERE d.market = 'GDAM' AND d.status = 'ACTIVE' %s) gdam
                ON COALESCE(dam.date, rtm.date) = gdam.date AND COALESCE(dam.timeblock, rtm.timeblock) = gdam.timeblock
            ORDER BY date DESC, timeblock ASC
            LIMIT 1000
            """.formatted(filter, filter, filter, filter);

        try {
            Query query = entityManager.createNativeQuery(sql);
            if (startDate != null && !startDate.trim().isEmpty()) {
                query.setParameter("startDate", LocalDate.parse(startDate.trim()));
            }
            if (endDate != null && !endDate.trim().isEmpty()) {
                query.setParameter("endDate", LocalDate.parse(endDate.trim()));
            }

            List<Object[]> rows = query.getResultList();
            List<Map<String, Object>> result = new ArrayList<>();
            int idx = 0;
            for (Object[] row : rows) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", "virtual-" + idx);
                map.put("date", row[0] != null ? row[0].toString() : null);
                map.put("timeblock", row[1] != null ? ((Number) row[1]).intValue() : null);
                map.put("damMcp", row[2] != null ? ((Number) row[2]).doubleValue() : 0.0);
                map.put("rtmMcp", row[3] != null ? ((Number) row[3]).doubleValue() : 0.0);
                map.put("gdamMcp", row[4] != null ? ((Number) row[4]).doubleValue() : 0.0);
                result.add(map);
                idx++;
            }
            return result;
        } catch (Exception e) {
            log.error("Error executing market operations query: {}", e.getMessage());
            return List.of();
        }
    }
}
