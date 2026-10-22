package com.sense.prolt_operations.entity.forecasting;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "GdamMcpForecast", schema = "forecasting")
public class ForecastingGdammcpforecast implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "interval_number")
    private Integer intervalNumber;

    @Column(name = "interval_time")
    private String intervalTime;

    @Column(name = "forecast_mcp")
    private BigDecimal forecastMcp;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Column(name = "price_range")
    private String priceRange;

    @Column(name = "model_number")
    private Integer modelNumber;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "forecasting_date")
    private LocalDate forecastingDate;

    @Column(name = "forecast_date")
    private LocalDate forecastDate;

    @Column(name = "forecasting_for")
    private LocalDate forecastingFor;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected ForecastingGdammcpforecast() {
    }

    public static ForecastingGdammcpforecast create() {
        return new ForecastingGdammcpforecast();
    }

    public ForecastingGdammcpforecast(Integer id, LocalDate date, Integer intervalNumber, String intervalTime, BigDecimal forecastMcp) {
        this.id = id;
        this.date = date;
        this.intervalNumber = intervalNumber;
        this.intervalTime = intervalTime;
        this.forecastMcp = forecastMcp;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getIntervalNumber() {
        return intervalNumber;
    }

    public void setIntervalNumber(Integer intervalNumber) {
        this.intervalNumber = intervalNumber;
    }

    public String getIntervalTime() {
        return intervalTime;
    }

    public void setIntervalTime(String intervalTime) {
        this.intervalTime = intervalTime;
    }

    public BigDecimal getForecastMcp() {
        return forecastMcp;
    }

    public void setForecastMcp(BigDecimal forecastMcp) {
        this.forecastMcp = forecastMcp;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getPriceRange() {
        return priceRange;
    }

    public void setPriceRange(String priceRange) {
        this.priceRange = priceRange;
    }

    public Integer getModelNumber() {
        return modelNumber;
    }

    public void setModelNumber(Integer modelNumber) {
        this.modelNumber = modelNumber;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public LocalDate getForecastingDate() {
        return forecastingDate;
    }

    public void setForecastingDate(LocalDate forecastingDate) {
        this.forecastingDate = forecastingDate;
    }

    public LocalDate getForecastDate() {
        return forecastDate;
    }

    public void setForecastDate(LocalDate forecastDate) {
        this.forecastDate = forecastDate;
    }

    public LocalDate getForecastingFor() {
        return forecastingFor;
    }

    public void setForecastingFor(LocalDate forecastingFor) {
        this.forecastingFor = forecastingFor;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ForecastingGdammcpforecast that = (ForecastingGdammcpforecast) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ForecastingGdammcpforecast{" + "id=" + id + ", " + "date=" + date + ", " + "intervalNumber=" + intervalNumber + ", " + "intervalTime=" + intervalTime + ", " + "forecastMcp=" + forecastMcp + ", " + "confidence=" + confidence + ", " + "priceRange=" + priceRange + ", " + "modelNumber=" + modelNumber + ", " + "modelVersion=" + modelVersion + ", " + "forecastingDate=" + forecastingDate + ", " + "forecastDate=" + forecastDate + ", " + "forecastingFor=" + forecastingFor + ", " + "createdAt=" + createdAt + "}";
    }
}
