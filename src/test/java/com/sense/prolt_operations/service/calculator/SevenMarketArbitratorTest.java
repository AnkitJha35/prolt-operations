package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.service.calculator.model.MarketSlotCalculation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SevenMarketArbitrator Unit Tests")
class SevenMarketArbitratorTest {

    private final SevenMarketArbitrator arbitrator = new SevenMarketArbitrator();

    @Test
    @DisplayName("Arbitrate should handle empty or null slot lists safely")
    void testEmptyOrNull() {
        arbitrator.arbitrate(null, 1000.0, 250.0);
        arbitrator.arbitrate(new ArrayList<>(), 1000.0, 250.0);
    }

    @Test
    @DisplayName("Arbitrate should select GDAM when it offers the lowest total landed cost")
    void testSelectGdamWhenCheapest() {
        List<MarketSlotCalculation> slots = new ArrayList<>();

        MarketSlotCalculation s1 = new MarketSlotCalculation();
        s1.date = "2026-06-01";
        s1.timeblock = 1;
        s1.shouldBuyFromMarket = true;
        s1.discomLanding = 8.0;
        s1.damLanding = 6.0;
        s1.rtmLanding = 7.0;
        s1.gdamLanding = 4.0;
        slots.add(s1);

        arbitrator.arbitrate(slots, 1000.0, 250.0);

        assertThat(s1.marketSource).isEqualTo("GDAM");
        assertThat(s1.bestMarketLanding).isEqualTo(4.0);
        assertThat(s1.shouldBuyFromMarket).isTrue();
    }

    @Test
    @DisplayName("Arbitrate should disregard non-marketable slots")
    void testIgnoreNonMarketableSlots() {
        List<MarketSlotCalculation> slots = new ArrayList<>();

        MarketSlotCalculation s1 = new MarketSlotCalculation();
        s1.date = "2026-06-01";
        s1.timeblock = 1;
        s1.shouldBuyFromMarket = false;
        s1.marketSource = "DISCOM";
        s1.discomLanding = 8.0;
        slots.add(s1);

        arbitrator.arbitrate(slots, 1000.0, 250.0);

        assertThat(s1.marketSource).isEqualTo("DISCOM");
        assertThat(s1.shouldBuyFromMarket).isFalse();
    }
}
