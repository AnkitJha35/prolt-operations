package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "StateDemandData", schema = "public")
public class StateDemandData implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "stateName")
    private String stateName;

    @Column(name = "date")
    private String date;

    @Column(name = "timeStr")
    private String timeStr;

    @Column(name = "demand")
    private Integer demand;

    @Column(name = "unit")
    private String unit;

    @Column(name = "region")
    private String region;

    @Column(name = "price")
    private Double price;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    protected StateDemandData() {
    }

    public static StateDemandData create() {
        return new StateDemandData();
    }

    public StateDemandData(String id, String stateName, String date, String timeStr, Integer demand) {
        this.id = id;
        this.stateName = stateName;
        this.date = date;
        this.timeStr = timeStr;
        this.demand = demand;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTimeStr() {
        return timeStr;
    }

    public void setTimeStr(String timeStr) {
        this.timeStr = timeStr;
    }

    public Integer getDemand() {
        return demand;
    }

    public void setDemand(Integer demand) {
        this.demand = demand;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
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
        StateDemandData that = (StateDemandData) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "StateDemandData{" + "id=" + id + ", " + "stateName=" + stateName + ", " + "date=" + date + ", " + "timeStr=" + timeStr + ", " + "demand=" + demand + ", " + "unit=" + unit + ", " + "region=" + region + ", " + "price=" + price + ", " + "updatedAt=" + updatedAt + "}";
    }
}
