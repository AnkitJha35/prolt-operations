package com.sense.prolt_operations.entity.forecasting;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "rtm_dayahead", schema = "forecasting")
public class RtmDayahead implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "date")
    private String date;

    @Column(name = "intervalNumber")
    private Short intervalNumber;

    @Column(name = "intervalTime")
    private String intervalTime;

    @Column(name = "forecastingFor")
    private LocalDate forecastingFor;

    @Column(name = "forecastDate")
    private LocalDateTime forecastDate;

    @Column(name = "predictedMcp")
    private BigDecimal predictedMcp;

    @Column(name = "predictedMcpP10")
    private BigDecimal predictedMcpP10;

    @Column(name = "predictedMcpP50")
    private BigDecimal predictedMcpP50;

    @Column(name = "predictedMcpP90")
    private BigDecimal predictedMcpP90;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Column(name = "price_range")
    private String priceRange;

    @Column(name = "modelVersion")
    private String modelVersion;

    @Column(name = "modelNumber")
    private Integer modelNumber;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    protected RtmDayahead() {
    }

    public static RtmDayahead create() {
        return new RtmDayahead();
    }

    public RtmDayahead(String id, String date, Short intervalNumber, String intervalTime, LocalDate forecastingFor) {
        this.id = id;
        this.date = date;
        this.intervalNumber = intervalNumber;
        this.intervalTime = intervalTime;
        this.forecastingFor = forecastingFor;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Short getIntervalNumber() {
        return intervalNumber;
    }

    public void setIntervalNumber(Short intervalNumber) {
        this.intervalNumber = intervalNumber;
    }

    public String getIntervalTime() {
        return intervalTime;
    }

    public void setIntervalTime(String intervalTime) {
        this.intervalTime = intervalTime;
    }

    public LocalDate getForecastingFor() {
        return forecastingFor;
    }

    public void setForecastingFor(LocalDate forecastingFor) {
        this.forecastingFor = forecastingFor;
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

    public BigDecimal getPredictedMcpP10() {
        return predictedMcpP10;
    }

    public void setPredictedMcpP10(BigDecimal predictedMcpP10) {
        this.predictedMcpP10 = predictedMcpP10;
    }

    public BigDecimal getPredictedMcpP50() {
        return predictedMcpP50;
    }

    public void setPredictedMcpP50(BigDecimal predictedMcpP50) {
        this.predictedMcpP50 = predictedMcpP50;
    }

    public BigDecimal getPredictedMcpP90() {
        return predictedMcpP90;
    }

    public void setPredictedMcpP90(BigDecimal predictedMcpP90) {
        this.predictedMcpP90 = predictedMcpP90;
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

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public Integer getModelNumber() {
        return modelNumber;
    }

    public void setModelNumber(Integer modelNumber) {
        this.modelNumber = modelNumber;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RtmDayahead that = (RtmDayahead) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RtmDayahead{" + "id=" + id + ", " + "date=" + date + ", " + "intervalNumber=" + intervalNumber + ", " + "intervalTime=" + intervalTime + ", " + "forecastingFor=" + forecastingFor + ", " + "forecastDate=" + forecastDate + ", " + "predictedMcp=" + predictedMcp + ", " + "predictedMcpP10=" + predictedMcpP10 + ", " + "predictedMcpP50=" + predictedMcpP50 + ", " + "predictedMcpP90=" + predictedMcpP90 + ", " + "confidence=" + confidence + ", " + "priceRange=" + priceRange + ", " + "modelVersion=" + modelVersion + ", " + "modelNumber=" + modelNumber + ", " + "createdAt=" + createdAt + ", " + "updatedAt=" + updatedAt + "}";
    }
}
