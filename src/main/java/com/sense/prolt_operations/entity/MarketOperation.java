package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "market_operation", schema = "public")
public class MarketOperation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "timeblock")
    private Integer timeblock;

    @Column(name = "dam_mcp")
    private BigDecimal damMcp;

    @Column(name = "rtm_mcp")
    private BigDecimal rtmMcp;

    @Column(name = "gdam_mcp")
    private BigDecimal gdamMcp;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    protected MarketOperation() {
    }

    public static MarketOperation create() {
        return new MarketOperation();
    }

    public MarketOperation(String id, LocalDate date, Integer timeblock, BigDecimal damMcp, BigDecimal rtmMcp) {
        this.id = id;
        this.date = date;
        this.timeblock = timeblock;
        this.damMcp = damMcp;
        this.rtmMcp = rtmMcp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getTimeblock() {
        return timeblock;
    }

    public void setTimeblock(Integer timeblock) {
        this.timeblock = timeblock;
    }

    public BigDecimal getDamMcp() {
        return damMcp;
    }

    public void setDamMcp(BigDecimal damMcp) {
        this.damMcp = damMcp;
    }

    public BigDecimal getRtmMcp() {
        return rtmMcp;
    }

    public void setRtmMcp(BigDecimal rtmMcp) {
        this.rtmMcp = rtmMcp;
    }

    public BigDecimal getGdamMcp() {
        return gdamMcp;
    }

    public void setGdamMcp(BigDecimal gdamMcp) {
        this.gdamMcp = gdamMcp;
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
        MarketOperation that = (MarketOperation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "MarketOperation{" + "id=" + id + ", " + "date=" + date + ", " + "timeblock=" + timeblock + ", " + "damMcp=" + damMcp + ", " + "rtmMcp=" + rtmMcp + ", " + "gdamMcp=" + gdamMcp + ", " + "createdAt=" + createdAt + ", " + "updatedAt=" + updatedAt + "}";
    }
}
