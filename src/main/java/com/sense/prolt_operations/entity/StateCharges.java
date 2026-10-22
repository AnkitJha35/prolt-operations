package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "state_charges", schema = "public")
public class StateCharges implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "state")
    private String state;

    @Column(name = "category")
    private String category;

    @Column(name = "sub_category")
    private String subCategory;

    @Column(name = "supply_voltage_category")
    private String supplyVoltageCategory;

    @Column(name = "voltage_level")
    private String voltageLevel;

    @Column(name = "from_date")
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @Column(name = "demand_fixed_charge_kva_per_month_rs")
    private BigDecimal demandFixedChargeKvaPerMonthRs;

    @Column(name = "cross_subsidy")
    private BigDecimal crossSubsidy;

    @Column(name = "distribution_wheeling_charges")
    private BigDecimal distributionWheelingCharges;

    @Column(name = "stu_charges")
    private BigDecimal stuCharges;

    @Column(name = "stu_loss_percent")
    private BigDecimal stuLossPercent;

    @Column(name = "wheeling_loss_percent")
    private BigDecimal wheelingLossPercent;

    @Column(name = "additional_charge")
    private BigDecimal additionalCharge;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "discom")
    private String discom;

    protected StateCharges() {
    }

    public static StateCharges create() {
        return new StateCharges();
    }

    public StateCharges(Integer id, String state, String category, String subCategory, String supplyVoltageCategory) {
        this.id = id;
        this.state = state;
        this.category = category;
        this.subCategory = subCategory;
        this.supplyVoltageCategory = supplyVoltageCategory;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubCategory() {
        return subCategory;
    }

    public void setSubCategory(String subCategory) {
        this.subCategory = subCategory;
    }

    public String getSupplyVoltageCategory() {
        return supplyVoltageCategory;
    }

    public void setSupplyVoltageCategory(String supplyVoltageCategory) {
        this.supplyVoltageCategory = supplyVoltageCategory;
    }

    public String getVoltageLevel() {
        return voltageLevel;
    }

    public void setVoltageLevel(String voltageLevel) {
        this.voltageLevel = voltageLevel;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public BigDecimal getDemandFixedChargeKvaPerMonthRs() {
        return demandFixedChargeKvaPerMonthRs;
    }

    public void setDemandFixedChargeKvaPerMonthRs(BigDecimal demandFixedChargeKvaPerMonthRs) {
        this.demandFixedChargeKvaPerMonthRs = demandFixedChargeKvaPerMonthRs;
    }

    public BigDecimal getCrossSubsidy() {
        return crossSubsidy;
    }

    public void setCrossSubsidy(BigDecimal crossSubsidy) {
        this.crossSubsidy = crossSubsidy;
    }

    public BigDecimal getDistributionWheelingCharges() {
        return distributionWheelingCharges;
    }

    public void setDistributionWheelingCharges(BigDecimal distributionWheelingCharges) {
        this.distributionWheelingCharges = distributionWheelingCharges;
    }

    public BigDecimal getStuCharges() {
        return stuCharges;
    }

    public void setStuCharges(BigDecimal stuCharges) {
        this.stuCharges = stuCharges;
    }

    public BigDecimal getStuLossPercent() {
        return stuLossPercent;
    }

    public void setStuLossPercent(BigDecimal stuLossPercent) {
        this.stuLossPercent = stuLossPercent;
    }

    public BigDecimal getWheelingLossPercent() {
        return wheelingLossPercent;
    }

    public void setWheelingLossPercent(BigDecimal wheelingLossPercent) {
        this.wheelingLossPercent = wheelingLossPercent;
    }

    public BigDecimal getAdditionalCharge() {
        return additionalCharge;
    }

    public void setAdditionalCharge(BigDecimal additionalCharge) {
        this.additionalCharge = additionalCharge;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StateCharges that = (StateCharges) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "StateCharges{" + "id=" + id + ", " + "state=" + state + ", " + "category=" + category + ", " + "subCategory=" + subCategory + ", " + "supplyVoltageCategory=" + supplyVoltageCategory + ", " + "voltageLevel=" + voltageLevel + ", " + "fromDate=" + fromDate + ", " + "toDate=" + toDate + ", " + "demandFixedChargeKvaPerMonthRs=" + demandFixedChargeKvaPerMonthRs + ", " + "crossSubsidy=" + crossSubsidy + ", " + "distributionWheelingCharges=" + distributionWheelingCharges + ", " + "stuCharges=" + stuCharges + ", " + "stuLossPercent=" + stuLossPercent + ", " + "wheelingLossPercent=" + wheelingLossPercent + ", " + "additionalCharge=" + additionalCharge + ", " + "updatedAt=" + updatedAt + ", " + "discom=" + discom + "}";
    }
}
