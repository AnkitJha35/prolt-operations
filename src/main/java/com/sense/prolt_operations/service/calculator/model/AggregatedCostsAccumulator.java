package com.sense.prolt_operations.service.calculator.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Type-safe accumulator for multi-month financial and technical cost rollups
 * in the client overview service ({@code getClientOverview}).
 *
 * <p>Replaces loosely typed {@code Map<String, Object>} dictionary manipulation with
 * strongly typed fields and explicit accumulation methods.
 */
public class AggregatedCostsAccumulator {

    private double cssCharge;
    private double rpoCharge;
    private double pocCharge;
    private double stuCharge;
    private double dcCharge;
    private double iexFee;
    private double traderMarginTotal;
    private double dailyFixedOverhead;
    private double bidApplicationFees;
    private double proltMarginCost;
    private double consultancyFee;
    private double probusPlatformFee;
    private double totalDiscomCost;
    private double energyCharges;
    private double demandAndFixedCharges;
    private double penaltiesAndAdjustments;
    private double miscellaneousCharges;
    private double peakDemand;
    private double demandChargeRate;

    public AggregatedCostsAccumulator() {
        // Defaults to 0.0
    }

    public void addCssCharge(double value) {
        this.cssCharge += value;
    }

    public void addRpoCharge(double value) {
        this.rpoCharge += value;
    }

    public void addPocCharge(double value) {
        this.pocCharge += value;
    }

    public void addStuCharge(double value) {
        this.stuCharge += value;
    }

    public void addDcCharge(double value) {
        this.dcCharge += value;
    }

    public void addIexFee(double value) {
        this.iexFee += value;
    }

    public void addTraderMarginTotal(double value) {
        this.traderMarginTotal += value;
    }

    public void addDailyFixedOverhead(double value) {
        this.dailyFixedOverhead += value;
    }

    public void addBidApplicationFees(double value) {
        this.bidApplicationFees += value;
    }

    public void addProltMarginCost(double value) {
        this.proltMarginCost += value;
    }

    public void addConsultancyFee(double value) {
        this.consultancyFee += value;
    }

    public void addProbusPlatformFee(double value) {
        this.probusPlatformFee += value;
    }

    public void addTotalDiscomCost(double value) {
        this.totalDiscomCost += value;
    }

    public void addEnergyCharges(double value) {
        this.energyCharges += value;
    }

    public void addDemandAndFixedCharges(double value) {
        this.demandAndFixedCharges += value;
    }

    public void addPenaltiesAndAdjustments(double value) {
        this.penaltiesAndAdjustments += value;
    }

    public void addMiscellaneousCharges(double value) {
        this.miscellaneousCharges += value;
    }

    public void updatePeakDemand(double peakDemand) {
        this.peakDemand = Math.max(this.peakDemand, peakDemand);
    }

    public void setDemandChargeRate(double demandChargeRate) {
        this.demandChargeRate = demandChargeRate;
    }

    // Getters
    public double getCssCharge() { return cssCharge; }
    public double getRpoCharge() { return rpoCharge; }
    public double getPocCharge() { return pocCharge; }
    public double getStuCharge() { return stuCharge; }
    public double getDcCharge() { return dcCharge; }
    public double getIexFee() { return iexFee; }
    public double getTraderMarginTotal() { return traderMarginTotal; }
    public double getDailyFixedOverhead() { return dailyFixedOverhead; }
    public double getBidApplicationFees() { return bidApplicationFees; }
    public double getProltMarginCost() { return proltMarginCost; }
    public double getConsultancyFee() { return consultancyFee; }
    public double getProbusPlatformFee() { return probusPlatformFee; }
    public double getTotalDiscomCost() { return totalDiscomCost; }
    public double getEnergyCharges() { return energyCharges; }
    public double getDemandAndFixedCharges() { return demandAndFixedCharges; }
    public double getPenaltiesAndAdjustments() { return penaltiesAndAdjustments; }
    public double getMiscellaneousCharges() { return miscellaneousCharges; }
    public double getPeakDemand() { return peakDemand; }
    public double getDemandChargeRate() { return demandChargeRate; }

    /**
     * Converts this strongly typed accumulator into an untyped {@link Map} matching
     * the legacy JSON response contract.
     *
     * @return Map with exact legacy keys in deterministic insertion order.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cssCharge", cssCharge);
        map.put("rpoCharge", rpoCharge);
        map.put("pocCharge", pocCharge);
        map.put("stuCharge", stuCharge);
        map.put("dcCharge", dcCharge);
        map.put("iexFee", iexFee);
        map.put("traderMarginTotal", traderMarginTotal);
        map.put("dailyFixedOverhead", dailyFixedOverhead);
        map.put("bidApplicationFees", bidApplicationFees);
        map.put("proltMarginCost", proltMarginCost);
        map.put("consultancyFee", consultancyFee);
        map.put("probusPlatformFee", probusPlatformFee);
        map.put("totalDiscomCost", totalDiscomCost);
        map.put("energyCharges", energyCharges);
        map.put("demandAndFixedCharges", demandAndFixedCharges);
        map.put("penaltiesAndAdjustments", penaltiesAndAdjustments);
        map.put("miscellaneousCharges", miscellaneousCharges);
        map.put("peakDemand", peakDemand);
        map.put("demandChargeRate", demandChargeRate);
        return map;
    }
}
