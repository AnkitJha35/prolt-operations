package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "fppa_charges", schema = "public")
public class FppaCharges implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "state")
    private String state;

    @Column(name = "month")
    private Integer month;

    @Column(name = "fppa_charge_percent")
    private BigDecimal fppaChargePercent;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "discom")
    private String discom;

    @Column(name = "billing_month")
    private Integer billingMonth;

    protected FppaCharges() {
    }

    public static FppaCharges create() {
        return new FppaCharges();
    }

    public FppaCharges(Integer id, String state, Integer month, BigDecimal fppaChargePercent, LocalDateTime updatedAt) {
        this.id = id;
        this.state = state;
        this.month = month;
        this.fppaChargePercent = fppaChargePercent;
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

    public BigDecimal getFppaChargePercent() {
        return fppaChargePercent;
    }

    public void setFppaChargePercent(BigDecimal fppaChargePercent) {
        this.fppaChargePercent = fppaChargePercent;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getDiscom() {
        return discom;
    }

    public void setDiscom(String discom) {
        this.discom = discom;
    }

    public Integer getBillingMonth() {
        return billingMonth;
    }

    public void setBillingMonth(Integer billingMonth) {
        this.billingMonth = billingMonth;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FppaCharges that = (FppaCharges) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "FppaCharges{" + "id=" + id + ", " + "state=" + state + ", " + "month=" + month + ", " + "fppaChargePercent=" + fppaChargePercent + ", " + "updatedAt=" + updatedAt + ", " + "discom=" + discom + ", " + "billingMonth=" + billingMonth + "}";
    }
}
