package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "prolt_margin", schema = "public")
public class ProltMargin implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "month")
    private Integer month;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "trading_margin")
    private BigDecimal tradingMargin;

    @Column(name = "trading_margin_gst")
    private BigDecimal tradingMarginGst;

    @Column(name = "prolt_margin")
    private BigDecimal proltMargin;

    @Column(name = "prolt_margin_gst")
    private BigDecimal proltMarginGst;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected ProltMargin() {
    }

    public static ProltMargin create() {
        return new ProltMargin();
    }

    public ProltMargin(Integer month, String customerId, BigDecimal tradingMargin, BigDecimal tradingMarginGst, BigDecimal proltMargin) {
        this.month = month;
        this.customerId = customerId;
        this.tradingMargin = tradingMargin;
        this.tradingMarginGst = tradingMarginGst;
        this.proltMargin = proltMargin;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getTradingMargin() {
        return tradingMargin;
    }

    public void setTradingMargin(BigDecimal tradingMargin) {
        this.tradingMargin = tradingMargin;
    }

    public BigDecimal getTradingMarginGst() {
        return tradingMarginGst;
    }

    public void setTradingMarginGst(BigDecimal tradingMarginGst) {
        this.tradingMarginGst = tradingMarginGst;
    }

    public BigDecimal getProltMargin() {
        return proltMargin;
    }

    public void setProltMargin(BigDecimal proltMargin) {
        this.proltMargin = proltMargin;
    }

    public BigDecimal getProltMarginGst() {
        return proltMarginGst;
    }

    public void setProltMarginGst(BigDecimal proltMarginGst) {
        this.proltMarginGst = proltMarginGst;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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
        ProltMargin that = (ProltMargin) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ProltMargin{" + "month=" + month + ", " + "customerId=" + customerId + ", " + "tradingMargin=" + tradingMargin + ", " + "tradingMarginGst=" + tradingMarginGst + ", " + "proltMargin=" + proltMargin + ", " + "proltMarginGst=" + proltMarginGst + ", " + "id=" + id + ", " + "updatedAt=" + updatedAt + "}";
    }
}
