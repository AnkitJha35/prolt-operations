package com.sense.prolt_operations.entity.forecasting;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "dam_forecasting", schema = "forecasting")
public class DamForecasting implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "forecasting_for")
    private LocalDateTime forecastingFor;

    @Column(name = "time_block")
    private Short timeBlock;

    @Column(name = "model_number")
    private Integer modelNumber;

    @Column(name = "forecast_date")
    private LocalDateTime forecastDate;

    @Column(name = "predicted_mcp")
    private BigDecimal predictedMcp;

    @Column(name = "confidence_pct")
    private BigDecimal confidencePct;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "price_range")
    private String priceRange;

    protected DamForecasting() {
    }

    public static DamForecasting create() {
        return new DamForecasting();
    }

    public DamForecasting(LocalDateTime forecastingFor, Short timeBlock, Integer modelNumber, LocalDateTime forecastDate, BigDecimal predictedMcp) {
        this.forecastingFor = forecastingFor;
        this.timeBlock = timeBlock;
        this.modelNumber = modelNumber;
        this.forecastDate = forecastDate;
        this.predictedMcp = predictedMcp;
    }

    public LocalDateTime getForecastingFor() {
        return forecastingFor;
    }

    public void setForecastingFor(LocalDateTime forecastingFor) {
        this.forecastingFor = forecastingFor;
    }

    public Short getTimeBlock() {
        return timeBlock;
    }

    public void setTimeBlock(Short timeBlock) {
        this.timeBlock = timeBlock;
    }

    public Integer getModelNumber() {
        return modelNumber;
    }

    public void setModelNumber(Integer modelNumber) {
        this.modelNumber = modelNumber;
    }

    public LocalDateTime getForecastDate() {
        return forecastDate;
    }

    public void setForecastDate(LocalDateTime forecastDate) {
        this.forecastDate = forecastDate;
    }

    public BigDecimal getPredictedMcp() {
        return predictedMcp;
    }

    public void setPredictedMcp(BigDecimal predictedMcp) {
        this.predictedMcp = predictedMcp;
    }

    public BigDecimal getConfidencePct() {
        return confidencePct;
    }

    public void setConfidencePct(BigDecimal confidencePct) {
        this.confidencePct = confidencePct;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPriceRange() {
        return priceRange;
    }

    public void setPriceRange(String priceRange) {
        this.priceRange = priceRange;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DamForecasting that = (DamForecasting) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DamForecasting{" + "forecastingFor=" + forecastingFor + ", " + "timeBlock=" + timeBlock + ", " + "modelNumber=" + modelNumber + ", " + "forecastDate=" + forecastDate + ", " + "predictedMcp=" + predictedMcp + ", " + "confidencePct=" + confidencePct + ", " + "createdAt=" + createdAt + ", " + "updatedAt=" + updatedAt + ", " + "id=" + id + ", " + "priceRange=" + priceRange + "}";
    }
}
