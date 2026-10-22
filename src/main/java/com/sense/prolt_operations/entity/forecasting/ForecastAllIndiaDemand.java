package com.sense.prolt_operations.entity.forecasting;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "all_india_demand_forecasting", schema = "forecasting")
public class ForecastAllIndiaDemand implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "date")
    private String date;

    @Column(name = "hour")
    private String hour;

    @Column(name = "intervalNumber")
    private Short intervalNumber;

    @Column(name = "timeBlock")
    private String timeBlock;

    @Column(name = "forecastedDemand")
    private BigDecimal forecastedDemand;

    @Column(name = "actualDemand")
    private BigDecimal actualDemand;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    protected ForecastAllIndiaDemand() {
    }

    public static ForecastAllIndiaDemand create() {
        return new ForecastAllIndiaDemand();
    }

    public ForecastAllIndiaDemand(String id, String date, String hour, Short intervalNumber, String timeBlock) {
        this.id = id;
        this.date = date;
        this.hour = hour;
        this.intervalNumber = intervalNumber;
        this.timeBlock = timeBlock;
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

    public String getHour() {
        return hour;
    }

    public void setHour(String hour) {
        this.hour = hour;
    }

    public Short getIntervalNumber() {
        return intervalNumber;
    }

    public void setIntervalNumber(Short intervalNumber) {
        this.intervalNumber = intervalNumber;
    }

    public String getTimeBlock() {
        return timeBlock;
    }

    public void setTimeBlock(String timeBlock) {
        this.timeBlock = timeBlock;
    }

    public BigDecimal getForecastedDemand() {
        return forecastedDemand;
    }

    public void setForecastedDemand(BigDecimal forecastedDemand) {
        this.forecastedDemand = forecastedDemand;
    }

    public BigDecimal getActualDemand() {
        return actualDemand;
    }

    public void setActualDemand(BigDecimal actualDemand) {
        this.actualDemand = actualDemand;
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
        ForecastAllIndiaDemand that = (ForecastAllIndiaDemand) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ForecastAllIndiaDemand{" + "id=" + id + ", " + "date=" + date + ", " + "hour=" + hour + ", " + "intervalNumber=" + intervalNumber + ", " + "timeBlock=" + timeBlock + ", " + "forecastedDemand=" + forecastedDemand + ", " + "actualDemand=" + actualDemand + ", " + "createdAt=" + createdAt + ", " + "updatedAt=" + updatedAt + "}";
    }
}
