package com.sense.prolt_operations.entity.forecasting;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "rtm_nowcast", schema = "forecasting")
public class RtmNowcast implements Serializable {

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

    @Column(name = "forecastedAt")
    private LocalDateTime forecastedAt;

    @Column(name = "currentBlock")
    private Short currentBlock;

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

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    protected RtmNowcast() {
    }

    public static RtmNowcast create() {
        return new RtmNowcast();
    }

    public RtmNowcast(String id, String date, Short intervalNumber, String intervalTime, LocalDateTime forecastedAt) {
        this.id = id;
        this.date = date;
        this.intervalNumber = intervalNumber;
        this.intervalTime = intervalTime;
        this.forecastedAt = forecastedAt;
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

    public LocalDateTime getForecastedAt() {
        return forecastedAt;
    }

    public void setForecastedAt(LocalDateTime forecastedAt) {
        this.forecastedAt = forecastedAt;
    }

    public Short getCurrentBlock() {
        return currentBlock;
    }

    public void setCurrentBlock(Short currentBlock) {
        this.currentBlock = currentBlock;
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
        RtmNowcast that = (RtmNowcast) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RtmNowcast{" + "id=" + id + ", " + "date=" + date + ", " + "intervalNumber=" + intervalNumber + ", " + "intervalTime=" + intervalTime + ", " + "forecastedAt=" + forecastedAt + ", " + "currentBlock=" + currentBlock + ", " + "predictedMcp=" + predictedMcp + ", " + "predictedMcpP10=" + predictedMcpP10 + ", " + "predictedMcpP50=" + predictedMcpP50 + ", " + "predictedMcpP90=" + predictedMcpP90 + ", " + "confidence=" + confidence + ", " + "priceRange=" + priceRange + ", " + "modelVersion=" + modelVersion + ", " + "createdAt=" + createdAt + ", " + "updatedAt=" + updatedAt + "}";
    }
}
