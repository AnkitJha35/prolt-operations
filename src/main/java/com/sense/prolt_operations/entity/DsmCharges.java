package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "dsm_charges", schema = "public")
public class DsmCharges implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "state")
    private String state;

    @Column(name = "month")
    private Integer month;

    @Column(name = "dsm_charges_rs_per_kwh")
    private BigDecimal dsmChargesRsPerKwh;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected DsmCharges() {
    }

    public static DsmCharges create() {
        return new DsmCharges();
    }

    public DsmCharges(Integer id, String state, Integer month, BigDecimal dsmChargesRsPerKwh, LocalDateTime updatedAt) {
        this.id = id;
        this.state = state;
        this.month = month;
        this.dsmChargesRsPerKwh = dsmChargesRsPerKwh;
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

    public BigDecimal getDsmChargesRsPerKwh() {
        return dsmChargesRsPerKwh;
    }

    public void setDsmChargesRsPerKwh(BigDecimal dsmChargesRsPerKwh) {
        this.dsmChargesRsPerKwh = dsmChargesRsPerKwh;
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
        DsmCharges that = (DsmCharges) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DsmCharges{" + "id=" + id + ", " + "state=" + state + ", " + "month=" + month + ", " + "dsmChargesRsPerKwh=" + dsmChargesRsPerKwh + ", " + "updatedAt=" + updatedAt + "}";
    }
}
