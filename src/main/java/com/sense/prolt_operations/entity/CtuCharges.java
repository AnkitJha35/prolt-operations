package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "ctu_charges", schema = "public")
public class CtuCharges implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "state")
    private String state;

    @Column(name = "month")
    private Integer month;

    @Column(name = "ctu_charges_rs_per_kwh")
    private BigDecimal ctuChargesRsPerKwh;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected CtuCharges() {
    }

    public static CtuCharges create() {
        return new CtuCharges();
    }

    public CtuCharges(Integer id, String state, Integer month, BigDecimal ctuChargesRsPerKwh, LocalDateTime updatedAt) {
        this.id = id;
        this.state = state;
        this.month = month;
        this.ctuChargesRsPerKwh = ctuChargesRsPerKwh;
        this.updatedAt = updatedAt;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public BigDecimal getCtuChargesRsPerKwh() {
        return ctuChargesRsPerKwh;
    }

    public void setCtuChargesRsPerKwh(BigDecimal ctuChargesRsPerKwh) {
        this.ctuChargesRsPerKwh = ctuChargesRsPerKwh;
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
        CtuCharges that = (CtuCharges) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CtuCharges{" + "id=" + id + ", " + "state=" + state + ", " + "month=" + month + ", " + "ctuChargesRsPerKwh=" + ctuChargesRsPerKwh + ", " + "updatedAt=" + updatedAt + "}";
    }
}
