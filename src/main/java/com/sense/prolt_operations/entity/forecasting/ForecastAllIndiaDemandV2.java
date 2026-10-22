package com.sense.prolt_operations.entity.forecasting;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "all_india_demand_forecasting_v2", schema = "forecasting")
public class ForecastAllIndiaDemandV2 implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "forecast_demand")
    private Double forecastDemand;

    protected ForecastAllIndiaDemandV2() {
    }

    public static ForecastAllIndiaDemandV2 create() {
        return new ForecastAllIndiaDemandV2();
    }

    public ForecastAllIndiaDemandV2(LocalDateTime timestamp, Double forecastDemand) {
        this.timestamp = timestamp;
        this.forecastDemand = forecastDemand;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Double getForecastDemand() {
        return forecastDemand;
    }

    public void setForecastDemand(Double forecastDemand) {
        this.forecastDemand = forecastDemand;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ForecastAllIndiaDemandV2 that = (ForecastAllIndiaDemandV2) o;
        return Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp);
    }

    @Override
    public String toString() {
        return "ForecastAllIndiaDemandV2{" + "timestamp=" + timestamp + ", " + "forecastDemand=" + forecastDemand + "}";
    }
}
