package com.sense.prolt_operations.entity.forecasting;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "market_forecasting", schema = "forecasting")
public class MarketForecasting implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "date")
    private String date;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "slots")
    private String slots;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    protected MarketForecasting() {
    }

    public static MarketForecasting create() {
        return new MarketForecasting();
    }

    public MarketForecasting(String date, String slots, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.date = date;
        this.slots = slots;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getSlots() {
        return slots;
    }

    public void setSlots(String slots) {
        this.slots = slots;
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
        MarketForecasting that = (MarketForecasting) o;
        return Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date);
    }

    @Override
    public String toString() {
        return "MarketForecasting{" + "date=" + date + ", " + "slots=" + slots + ", " + "createdAt=" + createdAt + ", " + "updatedAt=" + updatedAt + "}";
    }
}
