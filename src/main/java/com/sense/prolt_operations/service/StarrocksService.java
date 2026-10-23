package com.sense.prolt_operations.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StarrocksService {

    private static final Logger log = LoggerFactory.getLogger(StarrocksService.class);

    @Value("${app.starrocks.host:}")
    private String host;

    @Value("${app.starrocks.port:9030}")
    private int port;

    @Value("${app.starrocks.user:}")
    private String user;

    @Value("${app.starrocks.password:}")
    private String password;

    @Value("${app.starrocks.database:prolt_hes_db}")
    private String database;

    private Connection getConnection() throws Exception {
        String url = String.format("jdbc:mysql://%s:%d/%s?connectTimeout=10000&socketTimeout=10000", host, port, database);
        return DriverManager.getConnection(url, user, password);
    }

    public List<String> getAvailableMeters() {
        if (host == null || host.isBlank()) {
            log.warn("StarRocks host is blank; skipping connection.");
            return java.util.Collections.emptyList();
        }
        List<String> meters = new ArrayList<>();
        String sql = "SELECT DISTINCT meter_number FROM prolt_load_data WHERE meter_number IS NOT NULL AND meter_number != ''";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                meters.add(rs.getString("meter_number"));
            }
        } catch (Exception e) {
            log.warn("StarRocks getAvailableMeters connection issue: {}", e.getMessage());
        }
        return meters;
    }

    public Map<String, Double> getConsumerActualDemandMap(List<String> dates, String meterNo) {
        if (host == null || host.isBlank()) {
            log.warn("StarRocks host is blank; skipping connection.");
            return java.util.Collections.emptyMap();
        }
        Map<String, Double> actualMap = new HashMap<>();
        if (dates == null || dates.isEmpty()) return actualMap;

        String targetMeter = (meterNo != null && !meterNo.isBlank()) ? meterNo.trim() : "X2521837";
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < dates.size(); i++) {
            placeholders.append(i == 0 ? "?" : ", ?");
        }

        String sql = String.format("""
            SELECT 
              DATE_FORMAT(datetime_slot, '%%Y-%%m-%%d') as date_str,
              DATE_FORMAT(datetime_slot, '%%H:%%i') as time_str,
              ROUND(MAX(block_apparent_energy * 0.4), 2) as total_apparent_kvah,
              ROUND(MAX(block_active_energy * 0.4), 2) as total_active_kwh
            FROM prolt_load_data 
            WHERE DATE_FORMAT(datetime_slot, '%%Y-%%m-%%d') IN (%s)
              AND meter_number = ?
            GROUP BY datetime_slot, date_str, time_str
            ORDER BY datetime_slot ASC
            """, placeholders);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < dates.size(); i++) {
                ps.setString(i + 1, dates.get(i));
            }
            ps.setString(dates.size() + 1, targetMeter);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String dateStr = rs.getString("date_str");
                    String timeStr = rs.getString("time_str");
                    double kvah = rs.getDouble("total_apparent_kvah");
                    double kwh = rs.getDouble("total_active_kwh");
                    double val = kvah > 0 ? kvah : kwh;

                    actualMap.put(dateStr + "_" + timeStr, val);

                    if (timeStr != null && timeStr.contains(":")) {
                        String[] parts = timeStr.split(":");
                        try {
                            int hh = Integer.parseInt(parts[0]);
                            int mm = Integer.parseInt(parts[1]);
                            int intervalNum = (hh * 4) + (mm / 15) + 1;
                            actualMap.put(dateStr + "_" + intervalNum, val);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            log.warn("StarRocks getConsumerActualDemandMap connection issue: {}", e.getMessage());
        }

        return actualMap;
    }
}
