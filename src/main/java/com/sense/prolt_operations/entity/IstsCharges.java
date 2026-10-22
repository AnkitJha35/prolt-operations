package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "ists_charges", schema = "public")
public class IstsCharges implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "ists_loss_percent")
    private BigDecimal istsLossPercent;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected IstsCharges() {
    }

    public static IstsCharges create() {
        return new IstsCharges();
    }

    public IstsCharges(Integer id, BigDecimal istsLossPercent, LocalDate endDate, LocalDate startDate, LocalDateTime updatedAt) {
        this.id = id;
        this.istsLossPercent = istsLossPercent;
        this.endDate = endDate;
        this.startDate = startDate;
        this.updatedAt = updatedAt;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public BigDecimal getIstsLossPercent() {
        return istsLossPercent;
    }

    public void setIstsLossPercent(BigDecimal istsLossPercent) {
        this.istsLossPercent = istsLossPercent;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
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
        IstsCharges that = (IstsCharges) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "IstsCharges{" + "id=" + id + ", " + "istsLossPercent=" + istsLossPercent + ", " + "endDate=" + endDate + ", " + "startDate=" + startDate + ", " + "updatedAt=" + updatedAt + "}";
    }
}
