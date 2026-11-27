package com.sense.prolt_operations.service.calculator.model;

/**
 * Strongly typed domain model representing a timeblock slot eligible for
 * demand shifting optimization in {@code calculateDemandShiftInsights}.
 *
 * <p>Tracks original vs. adjusted energy allocations, available capacity headroom,
 * and cost differentials.
 */
public class ShiftableSlot {

    public int originalIndex;
    public double costPerKwh;
    public double currentEnergy;
    public double originalEnergy;
    public double currentMarketEnergy;
    public double originalMarketEnergy;
    public double currentConsumedMarketEnergy;
    public double originalConsumedMarketEnergy;
    public double currentDiscomEnergy;
    public boolean shouldBuyFromMarket;
    public double headroom;
    public double lossMultiplier;
    public String date;
    public int timeblock;
    public String tod;

    public ShiftableSlot() {
    }
}
