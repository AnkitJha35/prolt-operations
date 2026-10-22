package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "exchange_dam_rate", schema = "public")
public class ExchangeDamRate implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "intervalTime")
    private LocalTime intervalTime;

    @Column(name = "mcp")
    private BigDecimal mcp;

    @Column(name = "mcv")
    private BigDecimal mcv;

    @Column(name = "purchaseBid")
    private BigDecimal purchaseBid;

    @Column(name = "sellBid")
    private BigDecimal sellBid;

    @Column(name = "state")
    private String state;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected ExchangeDamRate() {
    }

    public static ExchangeDamRate create() {
        return new ExchangeDamRate();
    }

    public ExchangeDamRate(Integer id, LocalDate date, LocalTime intervalTime, BigDecimal mcp, BigDecimal mcv) {
        this.id = id;
        this.date = date;
        this.intervalTime = intervalTime;
        this.mcp = mcp;
        this.mcv = mcv;
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

    public LocalTime getIntervalTime() {
        return intervalTime;
    }

    public void setIntervalTime(LocalTime intervalTime) {
        this.intervalTime = intervalTime;
    }

    public BigDecimal getMcp() {
        return mcp;
    }

    public void setMcp(BigDecimal mcp) {
        this.mcp = mcp;
    }

    public BigDecimal getMcv() {
        return mcv;
    }

    public void setMcv(BigDecimal mcv) {
        this.mcv = mcv;
    }

    public BigDecimal getPurchaseBid() {
        return purchaseBid;
    }

    public void setPurchaseBid(BigDecimal purchaseBid) {
        this.purchaseBid = purchaseBid;
    }

    public BigDecimal getSellBid() {
        return sellBid;
    }

    public void setSellBid(BigDecimal sellBid) {
        this.sellBid = sellBid;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExchangeDamRate that = (ExchangeDamRate) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ExchangeDamRate{" + "id=" + id + ", " + "date=" + date + ", " + "mcp=" + mcp + ", " + "mcv=" + mcv + ", " + "purchaseBid=" + purchaseBid + ", " + "sellBid=" + sellBid + ", " + "state=" + state + ", " + "updatedAt=" + updatedAt + "}";
    }
}
