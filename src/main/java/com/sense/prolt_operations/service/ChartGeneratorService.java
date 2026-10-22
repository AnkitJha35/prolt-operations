package com.sense.prolt_operations.service;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class ChartGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(ChartGeneratorService.class);

    public byte[] generateSavingsChart(List<Map<String, Object>> monthlyData, String averageMonthlySavings, String savingsInWords) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        if (monthlyData != null) {
            for (Map<String, Object> item : monthlyData) {
                String month = String.valueOf(item.getOrDefault("month", "Month"));
                double savings = 0.0;
                Object val = item.get("savings");
                if (val instanceof Number n) savings = n.doubleValue();
                dataset.addValue(savings, "Monthly Savings (₹)", month);
            }
        } else {
            dataset.addValue(250000.0, "Monthly Savings (₹)", "Jan 2026");
            dataset.addValue(310000.0, "Monthly Savings (₹)", "Feb 2026");
        }

        JFreeChart chart = ChartFactory.createBarChart(
            "Monthly Landed Cost Savings",
            "Month",
            "Savings (₹)",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );
        chart.setBackgroundPaint(Color.WHITE);
        return toPngBytes(chart, 600, 360);
    }

    public byte[] generateConsumptionMixChart(double oaPercentage, double discomPercentage, double oaUnits, double discomUnits) {
        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        dataset.setValue("Open Access (" + Math.round(oaPercentage) + "%)", oaPercentage);
        dataset.setValue("Discom (" + Math.round(discomPercentage) + "%)", discomPercentage);

        JFreeChart chart = ChartFactory.createPieChart(
            "Energy Procurement Mix",
            dataset,
            true,
            true,
            false
        );
        chart.setBackgroundPaint(Color.WHITE);
        return toPngBytes(chart, 560, 360);
    }

    public byte[] generateSpendComparisonChart(double actualSpend, double optimizedSpend, String monthLabel) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        dataset.addValue(actualSpend, "Actual Discom Spend", monthLabel);
        dataset.addValue(optimizedSpend, "Optimized IEX Spend", monthLabel);

        JFreeChart chart = ChartFactory.createBarChart(
            "Monthly Spend Comparison (" + monthLabel + ")",
            "Category",
            "Spend (₹)",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );
        chart.setBackgroundPaint(Color.WHITE);
        return toPngBytes(chart, 560, 360);
    }

    public byte[] generateDailySavingsChart(List<Map<String, Object>> dailyData, String monthLabel) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        if (dailyData != null && !dailyData.isEmpty()) {
            for (Map<String, Object> d : dailyData) {
                String day = String.valueOf(d.getOrDefault("day", "Day"));
                double savings = 0.0;
                Object val = d.get("savings");
                if (val instanceof Number n) savings = n.doubleValue();
                dataset.addValue(savings, "Daily Savings", day);
            }
        } else {
            for (int i = 1; i <= 30; i++) {
                dataset.addValue(10000.0 + (i * 200), "Daily Savings", String.valueOf(i));
            }
        }

        JFreeChart chart = ChartFactory.createLineChart(
            "Daily Cumulative Savings (" + monthLabel + ")",
            "Day of Month",
            "Savings (₹)",
            dataset,
            PlotOrientation.VERTICAL,
            true,
            true,
            false
        );
        chart.setBackgroundPaint(Color.WHITE);
        return toPngBytes(chart, 560, 340);
    }

    public byte[] generatePurchaseComparisonChart(double recommendedOaPercentage, double recommendedDiscomPercentage) {
        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        dataset.setValue("Recommended OA (" + Math.round(recommendedOaPercentage) + "%)", recommendedOaPercentage);
        dataset.setValue("Recommended Discom (" + Math.round(recommendedDiscomPercentage) + "%)", recommendedDiscomPercentage);

        JFreeChart chart = ChartFactory.createPieChart(
            "Recommended Purchase Allocation",
            dataset,
            true,
            true,
            false
        );
        chart.setBackgroundPaint(Color.WHITE);
        return toPngBytes(chart, 560, 360);
    }

    private byte[] toPngBytes(JFreeChart chart, int width, int height) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ChartUtils.writeChartAsPNG(out, chart, width, height);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("[ChartGeneratorService] Failed to render PNG chart:", e);
            return new byte[0];
        }
    }
}
