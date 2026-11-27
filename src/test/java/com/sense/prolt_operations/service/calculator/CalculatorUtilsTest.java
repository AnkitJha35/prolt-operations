package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.service.calculator.model.ParsedCategory;
import com.sense.prolt_operations.service.calculator.model.SimulationDateRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CalculatorUtils Unit Tests")
class CalculatorUtilsTest {

    @Test
    @DisplayName("parseHour should extract integer hour safely")
    void testParseHour() {
        assertThat(CalculatorUtils.parseHour("17:00")).isEqualTo(17);
        assertThat(CalculatorUtils.parseHour("5")).isEqualTo(5);
        assertThat(CalculatorUtils.parseHour("—")).isEqualTo(0);
        assertThat(CalculatorUtils.parseHour(null)).isEqualTo(0);
        assertThat(CalculatorUtils.parseHour("abc")).isEqualTo(0);
    }

    @Test
    @DisplayName("parseSupplyVoltageCategory should extract base voltage")
    void testParseSupplyVoltage() {
        assertThat(CalculatorUtils.parseSupplyVoltageCategory("11 kV")).isEqualTo("11 kV");
        assertThat(CalculatorUtils.parseSupplyVoltageCategory("33 kV - HT")).isEqualTo("33 kV");
        assertThat(CalculatorUtils.parseSupplyVoltageCategory(null)).isEqualTo("11 kV");
        assertThat(CalculatorUtils.parseSupplyVoltageCategory("   ")).isEqualTo("11 kV");
    }

    @Test
    @DisplayName("parseCategory should resolve known patterns")
    void testParseCategory() {
        ParsedCategory hv1a = CalculatorUtils.parseCategory("HV-1 A");
        assertThat(hv1a.category()).isEqualTo("HV-1");
        assertThat(hv1a.subCategory()).isEqualTo("Commercial, Private Inst");

        ParsedCategory pipe = CalculatorUtils.parseCategory("Industrial | General");
        assertThat(pipe.category()).isEqualTo("Industrial");
        assertThat(pipe.subCategory()).isEqualTo("General");

        ParsedCategory empty = CalculatorUtils.parseCategory(null);
        assertThat(empty.category()).isEqualTo("Industrial");
        assertThat(empty.subCategory()).isNull();
    }

    @Test
    @DisplayName("getDouble and getMap should handle missing or invalid keys safely")
    void testGetDoubleAndGetMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("num", 42.5);
        map.put("strNum", "100.25");
        map.put("badNum", "not-a-number");

        Map<String, Object> nested = Map.of("inner", 123);
        map.put("nested", nested);

        assertThat(CalculatorUtils.getDouble(map, "num")).isEqualTo(42.5);
        assertThat(CalculatorUtils.getDouble(map, "strNum")).isEqualTo(100.25);
        assertThat(CalculatorUtils.getDouble(map, "badNum")).isEqualTo(0.0);
        assertThat(CalculatorUtils.getDouble(map, "missing")).isEqualTo(0.0);
        assertThat(CalculatorUtils.getDouble(null, "num")).isEqualTo(0.0);

        assertThat(CalculatorUtils.getMap(map, "nested")).isEqualTo(nested);
        assertThat(CalculatorUtils.getMap(map, "missing")).isEqualTo(Collections.emptyMap());
        assertThat(CalculatorUtils.getMap(null, "nested")).isEqualTo(Collections.emptyMap());
    }

    @Test
    @DisplayName("resolveSimulationDateRange should compute date boundaries and active months")
    void testResolveSimulationDateRange() {
        Map<String, Object> consumptions = new HashMap<>();
        consumptions.put("Start Date", 1);
        consumptions.put("End Date", 30);

        SimulationDateRange range = CalculatorUtils.resolveSimulationDateRange("2026-06", consumptions, "NPCL");
        assertThat(range.year()).isEqualTo(2026);
        assertThat(range.month()).isEqualTo(6);
        assertThat(range.startDate()).isNotNull();
        assertThat(range.endDate()).isNotNull();
        assertThat(range.monthsInPlay()).isNotEmpty();
    }
}
