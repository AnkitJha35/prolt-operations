package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "iex_fees", schema = "public")
public class IexFees implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "month")
    private Integer month;

    @Column(name = "exchange_fees")
    private BigDecimal exchangeFees;

    @Column(name = "exchange_fees_gst")
    private BigDecimal exchangeFeesGst;

    @Column(name = "nldc_application_fees")
    private BigDecimal nldcApplicationFees;

    @Column(name = "nldc_scheduling_fees")
    private BigDecimal nldcSchedulingFees;

    @Column(name = "sldc_scheduling_fees")
    private BigDecimal sldcSchedulingFees;

    @Column(name = "other_fix_charges")
    private BigDecimal otherFixCharges;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected IexFees() {
    }

    public static IexFees create() {
        return new IexFees();
    }

    public IexFees(Integer id, Integer month, BigDecimal exchangeFees, BigDecimal exchangeFeesGst, BigDecimal nldcApplicationFees) {
        this.id = id;
        this.month = month;
        this.exchangeFees = exchangeFees;
        this.exchangeFeesGst = exchangeFeesGst;
        this.nldcApplicationFees = nldcApplicationFees;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public BigDecimal getExchangeFees() {
        return exchangeFees;
    }

    public void setExchangeFees(BigDecimal exchangeFees) {
        this.exchangeFees = exchangeFees;
    }

    public BigDecimal getExchangeFeesGst() {
        return exchangeFeesGst;
    }

    public void setExchangeFeesGst(BigDecimal exchangeFeesGst) {
        this.exchangeFeesGst = exchangeFeesGst;
    }

    public BigDecimal getNldcApplicationFees() {
        return nldcApplicationFees;
    }

    public void setNldcApplicationFees(BigDecimal nldcApplicationFees) {
        this.nldcApplicationFees = nldcApplicationFees;
    }

    public BigDecimal getNldcSchedulingFees() {
        return nldcSchedulingFees;
    }

    public void setNldcSchedulingFees(BigDecimal nldcSchedulingFees) {
        this.nldcSchedulingFees = nldcSchedulingFees;
    }

    public BigDecimal getSldcSchedulingFees() {
        return sldcSchedulingFees;
    }

    public void setSldcSchedulingFees(BigDecimal sldcSchedulingFees) {
        this.sldcSchedulingFees = sldcSchedulingFees;
    }

    public BigDecimal getOtherFixCharges() {
        return otherFixCharges;
    }

    public void setOtherFixCharges(BigDecimal otherFixCharges) {
        this.otherFixCharges = otherFixCharges;
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
        IexFees that = (IexFees) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "IexFees{" + "id=" + id + ", " + "month=" + month + ", " + "exchangeFees=" + exchangeFees + ", " + "exchangeFeesGst=" + exchangeFeesGst + ", " + "nldcApplicationFees=" + nldcApplicationFees + ", " + "nldcSchedulingFees=" + nldcSchedulingFees + ", " + "sldcSchedulingFees=" + sldcSchedulingFees + ", " + "otherFixCharges=" + otherFixCharges + ", " + "updatedAt=" + updatedAt + "}";
    }
}
