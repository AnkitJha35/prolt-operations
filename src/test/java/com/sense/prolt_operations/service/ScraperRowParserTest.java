package com.sense.prolt_operations.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ScraperRowParserTest {

    @Test
    @DisplayName("DAM: parse 8-column, 7-column, 6-column, and 5-column formats")
    void testParseDamRows() {
        List<List<String>> rows = List.of(
                // Header / invalid rows to skip
                List.of("Date", "Hour", "Time Block", "Purchase Bid", "Sell Bid", "MCV", "FSV", "MCP"),
                List.of("Total", "-", "-", "-", "-", "-", "-", "-"),
                // 8-column row with date
                List.of("10-10-2026", "1", "00:00 - 00:15", "5,120.00", "4,900.00", "4,210.50", "4,210.50", "3,450.25"),
                // 7-column row with hour
                List.of("1", "00:15 - 00:30", "5,100", "4,800", "4,200", "4,200", "3,400"),
                // 6-column row
                List.of("00:30 - 00:45", "5000", "4700", "4100", "4100", "3300"),
                // 5-column row (purchaseBid:1, sellBid:2, mcv:3, mcp:4)
                List.of("00:45 - 01:00", "5050", "4750", "4150", "3350")
        );

        List<Map<String, Object>> records = ScraperService.parseDamRows(rows);
        assertThat(records).hasSize(4);

        Map<String, Object> r1 = records.get(0);
        assertThat(r1.get("date")).isEqualTo("10-10-2026");
        assertThat(r1.get("intervalNumber")).isEqualTo(1);
        assertThat(r1.get("intervalTime")).isEqualTo("00:00");
        assertThat(r1.get("purchaseBid")).isEqualTo(5120.0);
        assertThat(r1.get("sellBid")).isEqualTo(4900.0);
        assertThat(r1.get("mcv")).isEqualTo(4210.5);
        assertThat(r1.get("fsv")).isEqualTo(4210.5);
        assertThat(r1.get("mcp")).isEqualTo(3450.25);

        Map<String, Object> r2 = records.get(1);
        assertThat(r2.get("intervalNumber")).isEqualTo(2);
        assertThat(r2.get("intervalTime")).isEqualTo("00:15");
        assertThat(r2.get("purchaseBid")).isEqualTo(5100.0);
        assertThat(r2.get("sellBid")).isEqualTo(4800.0);
        assertThat(r2.get("mcv")).isEqualTo(4200.0);
        assertThat(r2.get("mcp")).isEqualTo(3400.0);

        Map<String, Object> r3 = records.get(2);
        assertThat(r3.get("intervalNumber")).isEqualTo(3);
        assertThat(r3.get("intervalTime")).isEqualTo("00:30");
        assertThat(r3.get("purchaseBid")).isEqualTo(5000.0);
        assertThat(r3.get("sellBid")).isEqualTo(4700.0);
        assertThat(r3.get("mcv")).isEqualTo(4100.0);
        assertThat(r3.get("mcp")).isEqualTo(3300.0);

        Map<String, Object> r4 = records.get(3);
        assertThat(r4.get("intervalNumber")).isEqualTo(4);
        assertThat(r4.get("intervalTime")).isEqualTo("00:45");
        assertThat(r4.get("purchaseBid")).isEqualTo(5050.0);
        assertThat(r4.get("sellBid")).isEqualTo(4750.0);
        assertThat(r4.get("mcv")).isEqualTo(4150.0);
        assertThat(r4.get("mcp")).isEqualTo(3350.0);
    }

    @Test
    @DisplayName("GDAM: parse multi-column and 5-column formats")
    void testParseGdamRows() {
        List<List<String>> rows = List.of(
                // 5-column simple row
                List.of("00:00 - 00:15", "1,200", "2,400", "1,800", "3,100"),
                // Multi-column row (18+ cols)
                List.of("15-10-2026", "1", "00:15 - 00:30",
                        "1000", "2000", "300", "400", "500", "600",
                        "1500", "250", "350", "450", "550",
                        "1500", "250", "350", "450", "550", "3200")
        );

        List<Map<String, Object>> records = ScraperService.parseGdamRows(rows);
        assertThat(records).hasSize(2);

        Map<String, Object> r1 = records.get(0);
        assertThat(r1.get("intervalNumber")).isEqualTo(1);
        assertThat(r1.get("intervalTime")).isEqualTo("00:00");
        assertThat(r1.get("purchaseBid")).isEqualTo(1200.0);
        assertThat(r1.get("sellBid")).isEqualTo(2400.0);
        assertThat(r1.get("mcv")).isEqualTo(1800.0);
        assertThat(r1.get("mcp")).isEqualTo(3100.0);

        Map<String, Object> r2 = records.get(1);
        assertThat(r2.get("date")).isEqualTo("15-10-2026");
        assertThat(r2.get("intervalNumber")).isEqualTo(2);
        assertThat(r2.get("intervalTime")).isEqualTo("00:15");
        assertThat(r2.get("purchaseBid")).isEqualTo(1000.0);
        assertThat(r2.get("sellBidTotal")).isEqualTo(2000.0);
        assertThat(r2.get("sellBidHydro")).isEqualTo(300.0);
        assertThat(r2.get("mcvTotal")).isEqualTo(1500.0);
        assertThat(r2.get("mcp")).isEqualTo(3200.0);
    }

    @Test
    @DisplayName("RTM: offset detection, future block skipping, index 3 or 4 for MCP")
    void testParseRtmRows() {
        List<List<String>> rows = List.of(
                // Row with MCP at offset + 5
                List.of("12-10-2026", "1", "00:00-00:15", "6,000", "5,500", "5,000", "5,000", "4,500"),
                // Row with MCP at offset + 4 (no separate FSV column)
                List.of("00:15-00:30", "6,100", "5,600", "5,100", "4,600"),
                // Future / unpopulated blocks with dashes or whitespace (should be skipped)
                List.of("00:30-00:45", "-", "-", "-", "-", "-"),
                List.of("00:45-01:00", "", " ", "", "", "")
        );

        List<Map<String, Object>> records = ScraperService.parseRtmRows(rows);
        assertThat(records).hasSize(2);

        Map<String, Object> r1 = records.get(0);
        assertThat(r1.get("date")).isEqualTo("12-10-2026");
        assertThat(r1.get("intervalNumber")).isEqualTo(1);
        assertThat(r1.get("intervalTime")).isEqualTo("00:00");
        assertThat(r1.get("sessionId")).isEqualTo("1");
        assertThat(r1.get("purchaseBid")).isEqualTo(6000.0);
        assertThat(r1.get("sellBid")).isEqualTo(5500.0);
        assertThat(r1.get("mcv")).isEqualTo(5000.0);
        assertThat(r1.get("fsv")).isEqualTo(5000.0);
        assertThat(r1.get("mcp")).isEqualTo(4500.0);

        Map<String, Object> r2 = records.get(1);
        assertThat(r2.get("intervalNumber")).isEqualTo(2);
        assertThat(r2.get("intervalTime")).isEqualTo("00:15");
        assertThat(r2.get("purchaseBid")).isEqualTo(6100.0);
        assertThat(r2.get("sellBid")).isEqualTo(5600.0);
        assertThat(r2.get("mcv")).isEqualTo(5100.0);
        assertThat(r2.get("mcp")).isEqualTo(4600.0);
    }

    @Test
    @DisplayName("REC: monthly records parsing for non-solar and solar")
    void testParseRecRows() {
        List<List<String>> rows = List.of(
                List.of("Year", "Month", "Type", "Buy Bids", "Sell Bids", "Cleared Vol", "Cleared Price", "Participants"),
                List.of("2024", "January\n10-01-2024", "Non-Solar", "12,500", "15,000", "10,000", "1,200.50", "45"),
                List.of("2024", "February\n14-02-2024", "Solar", "8,000", "9,500", "7,500", "1,150.00", "30"),
                List.of("2024", "March") // Invalid row to skip
        );

        List<Map<String, Object>> records = ScraperService.parseRecRows(rows);
        assertThat(records).hasSize(2);

        Map<String, Object> r1 = records.get(0);
        assertThat(r1.get("year")).isEqualTo(2024);
        assertThat(r1.get("month")).isEqualTo("January");
        assertThat(r1.get("type")).isEqualTo("Non-Solar");
        assertThat(r1.get("buyBids")).isEqualTo(12500.0);
        assertThat(r1.get("sellBids")).isEqualTo(15000.0);
        assertThat(r1.get("clearedVolume")).isEqualTo(10000.0);
        assertThat(r1.get("clearedPrice")).isEqualTo(1200.50);
        assertThat(r1.get("noOfParticipants")).isEqualTo(45);

        Map<String, Object> r2 = records.get(1);
        assertThat(r2.get("year")).isEqualTo(2024);
        assertThat(r2.get("month")).isEqualTo("February");
        assertThat(r2.get("type")).isEqualTo("Solar");
        assertThat(r2.get("buyBids")).isEqualTo(8000.0);
        assertThat(r2.get("sellBids")).isEqualTo(9500.0);
        assertThat(r2.get("clearedVolume")).isEqualTo(7500.0);
        assertThat(r2.get("clearedPrice")).isEqualTo(1150.00);
        assertThat(r2.get("noOfParticipants")).isEqualTo(30);
    }
}
