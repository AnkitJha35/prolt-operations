package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.service.calculator.model.MarketSlotCalculation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RtmContiguitySolver Unit Tests")
class RtmContiguitySolverTest {

    private final RtmContiguitySolver solver = new RtmContiguitySolver();

    @Test
    @DisplayName("Solver should handle null and empty slots safely")
    void testNullOrEmpty() {
        solver.resolveContiguity(null);
        solver.resolveContiguity(new ArrayList<>());
    }

    @Test
    @DisplayName("Contiguous RTM slots should remain unchanged")
    void testContiguousRtmPreserved() {
        List<MarketSlotCalculation> slots = new ArrayList<>();

        MarketSlotCalculation s1 = new MarketSlotCalculation();
        s1.marketSource = "RTM";
        s1.shouldBuyFromMarket = true;
        s1.rtmLanding = 4.0;
        s1.discomLanding = 8.0;

        MarketSlotCalculation s2 = new MarketSlotCalculation();
        s2.marketSource = "RTM";
        s2.shouldBuyFromMarket = true;
        s2.rtmLanding = 4.2;
        s2.discomLanding = 8.0;

        slots.add(s1);
        slots.add(s2);

        solver.resolveContiguity(slots);

        assertThat(s1.marketSource).isEqualTo("RTM");
        assertThat(s2.marketSource).isEqualTo("RTM");
    }

    @Test
    @DisplayName("Isolated RTM slot downgrades when downgrade penalty is lowest")
    void testIsolatedRtmDowngrade() {
        List<MarketSlotCalculation> slots = new ArrayList<>();

        MarketSlotCalculation s1 = new MarketSlotCalculation();
        s1.marketSource = "DAM";
        s1.shouldBuyFromMarket = true;
        s1.damLanding = 5.0;
        s1.rtmLanding = 9.0;
        s1.discomLanding = 8.0;

        MarketSlotCalculation s2 = new MarketSlotCalculation();
        s2.marketSource = "RTM";
        s2.shouldBuyFromMarket = true;
        s2.rtmLanding = 4.0;
        s2.damLanding = 4.2; // Alt price is DAM at 4.2 (penalty = 0.2)
        s2.discomLanding = 8.0;

        MarketSlotCalculation s3 = new MarketSlotCalculation();
        s3.marketSource = "DAM";
        s3.shouldBuyFromMarket = true;
        s3.damLanding = 5.0;
        s3.rtmLanding = 9.0;
        s3.discomLanding = 8.0;

        slots.add(s1);
        slots.add(s2);
        slots.add(s3);

        solver.resolveContiguity(slots);

        assertThat(s2.marketSource).isEqualTo("DAM");
        assertThat(s2.bestMarketLanding).isEqualTo(4.2);
    }
}
