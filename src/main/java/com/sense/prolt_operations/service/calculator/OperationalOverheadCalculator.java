package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.service.calculator.constant.OverheadFeeConstants;
import com.sense.prolt_operations.service.calculator.model.EnergyMarketType;
import com.sense.prolt_operations.service.calculator.model.MarketSlotCalculation;
import com.sense.prolt_operations.service.calculator.model.OperationalOverheads;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class OperationalOverheadCalculator {

    private final CalculatorProperties properties;

    public OperationalOverheadCalculator() {
        this(new CalculatorProperties());
    }

    @Autowired
    public OperationalOverheadCalculator(CalculatorProperties properties) {
        this.properties = properties != null ? properties : new CalculatorProperties();
    }

    public OperationalOverheads calculate(List<MarketSlotCalculation> slotsData, double nldcSchedulingFees, double sldcSchedulingFees) {
        Set<String> damDates = new HashSet<>();
        Set<String> gdamDates = new HashSet<>();
        Set<String> rtmDates = new HashSet<>();

        if (slotsData != null) {
            for (MarketSlotCalculation s : slotsData) {
                if (s.marketEnergy > 0 && s.marketSource != null) {
                    EnergyMarketType.fromValue(s.marketSource).ifPresent(market -> {
                        switch (market) {
                            case DAM -> damDates.add(s.date);
                            case GDAM -> gdamDates.add(s.date);
                            case RTM -> rtmDates.add(s.date);
                        }
                    });
                }
            }
        }

        int totalDamDays = damDates.size();
        int totalGdamDays = gdamDates.size();
        int totalRtmDays = rtmDates.size();
        Set<String> allTradedDates = new HashSet<>();
        allTradedDates.addAll(damDates);
        allTradedDates.addAll(gdamDates);
        allTradedDates.addAll(rtmDates);
        int totalDaysTraded = allTradedDates.size();

        double nldcSchedulingCost = nldcSchedulingFees * totalDaysTraded;
        double sldcSchedulingCost = sldcSchedulingFees * (totalDamDays + totalGdamDays + totalRtmDays);
        double dailyFixedOverhead = nldcSchedulingCost + sldcSchedulingCost;
        double feePerBid = properties != null && properties.getFees() != null
                ? properties.getFees().getNldcApplicationFeePerBid()
                : OverheadFeeConstants.NLDC_APPLICATION_FEE_PER_BID;
        double bidApplicationFees = (totalDamDays + totalGdamDays + totalRtmDays) * feePerBid;

        return new OperationalOverheads(
                totalDamDays,
                totalGdamDays,
                totalRtmDays,
                totalDaysTraded,
                nldcSchedulingCost,
                sldcSchedulingCost,
                dailyFixedOverhead,
                bidApplicationFees
        );
    }
}
