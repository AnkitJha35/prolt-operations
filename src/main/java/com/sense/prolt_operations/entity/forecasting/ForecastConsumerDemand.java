package com.sense.prolt_operations.entity.forecasting;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "consumer_demand_forecasting", schema = "forecasting")
@IdClass(ForecastConsumerDemandId.class)
public class ForecastConsumerDemand implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "slot_number")
    private Short slotNumber;

    @Column(name = "actual_energy")
    private BigDecimal actualEnergy;

    @Column(name = "forecasted_energy")
    private BigDecimal forecastedEnergy;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Id
    @Column(name = "meter_id")
    private String meterId;

    protected ForecastConsumerDemand() {
    }

    public static ForecastConsumerDemand create() {
        return new ForecastConsumerDemand();
    }

    public ForecastConsumerDemand(LocalDateTime timestamp, Short slotNumber, BigDecimal actualEnergy, BigDecimal forecastedEnergy, BigDecimal confidence) {
        this.timestamp = timestamp;
        this.slotNumber = slotNumber;
        this.actualEnergy = actualEnergy;
        this.forecastedEnergy = forecastedEnergy;
        this.confidence = confidence;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Short getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(Short slotNumber) {
        this.slotNumber = slotNumber;
    }

    public BigDecimal getActualEnergy() {
        return actualEnergy;
    }

    public void setActualEnergy(BigDecimal actualEnergy) {
        this.actualEnergy = actualEnergy;
    }

    public BigDecimal getForecastedEnergy() {
        return forecastedEnergy;
    }

    public void setForecastedEnergy(BigDecimal forecastedEnergy) {
        this.forecastedEnergy = forecastedEnergy;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getMeterId() {
        return meterId;
    }

    public void setMeterId(String meterId) {
        this.meterId = meterId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ForecastConsumerDemand that = (ForecastConsumerDemand) o;
        return Objects.equals(timestamp, that.timestamp) && Objects.equals(meterId, that.meterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, meterId);
    }

    @Override
    public String toString() {
        return "ForecastConsumerDemand{" + "timestamp=" + timestamp + ", " + "slotNumber=" + slotNumber + ", " + "actualEnergy=" + actualEnergy + ", " + "forecastedEnergy=" + forecastedEnergy + ", " + "confidence=" + confidence + ", " + "meterId=" + meterId + "}";
    }
}
