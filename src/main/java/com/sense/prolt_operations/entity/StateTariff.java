package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "state_tariff", schema = "public")
public class StateTariff implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "month")
    private Integer month;

    @Column(name = "state")
    private String state;

    @Column(name = "sub_category")
    private String subCategory;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "base_energy_rate")
    private BigDecimal baseEnergyRate;

    @Column(name = "base_energy_unit")
    private String baseEnergyUnit;

    @Column(name = "consumer_category")
    private String consumerCategory;

    @Column(name = "energy_rate")
    private BigDecimal energyRate;

    @Column(name = "supply_voltage")
    private String supplyVoltage;

    @Column(name = "supply_voltage_category")
    private String supplyVoltageCategory;

    @Column(name = "tod_charge_percent")
    private Integer todChargePercent;

    @Column(name = "tod_end_time")
    private String todEndTime;

    @Column(name = "tod_start_time")
    private String todStartTime;

    @Column(name = "discom")
    private String discom;

    @Column(name = "consumption_month")
    private Integer consumptionMonth;

    protected StateTariff() {
    }

    public static StateTariff create() {
        return new StateTariff();
    }

    public StateTariff(Integer month, String state, String subCategory, Integer id, LocalDateTime updatedAt) {
        this.month = month;
        this.state = state;
        this.subCategory = subCategory;
        this.id = id;
        this.updatedAt = updatedAt;
    }

    public Integer getMonth() {
        return month;
    }

    public void setMonth(Integer month) {
        this.month = month;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getSubCategory() {
        return subCategory;
    }

    public void setSubCategory(String subCategory) {
        this.subCategory = subCategory;
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

    public BigDecimal getBaseEnergyRate() {
        return baseEnergyRate;
    }

    public void setBaseEnergyRate(BigDecimal baseEnergyRate) {
        this.baseEnergyRate = baseEnergyRate;
    }

    public String getBaseEnergyUnit() {
        return baseEnergyUnit;
    }

    public void setBaseEnergyUnit(String baseEnergyUnit) {
        this.baseEnergyUnit = baseEnergyUnit;
    }

    public String getConsumerCategory() {
        return consumerCategory;
    }

    public void setConsumerCategory(String consumerCategory) {
        this.consumerCategory = consumerCategory;
    }

    public BigDecimal getEnergyRate() {
        return energyRate;
    }

    public void setEnergyRate(BigDecimal energyRate) {
        this.energyRate = energyRate;
    }

    public String getSupplyVoltage() {
        return supplyVoltage;
    }

    public void setSupplyVoltage(String supplyVoltage) {
        this.supplyVoltage = supplyVoltage;
    }

    public String getSupplyVoltageCategory() {
        return supplyVoltageCategory;
    }

    public void setSupplyVoltageCategory(String supplyVoltageCategory) {
        this.supplyVoltageCategory = supplyVoltageCategory;
    }

    public Integer getTodChargePercent() {
        return todChargePercent;
    }

    public void setTodChargePercent(Integer todChargePercent) {
        this.todChargePercent = todChargePercent;
    }

    public String getTodEndTime() {
        return todEndTime;
    }

    public void setTodEndTime(String todEndTime) {
        this.todEndTime = todEndTime;
    }

    public String getTodStartTime() {
        return todStartTime;
    }

    public void setTodStartTime(String todStartTime) {
        this.todStartTime = todStartTime;
    }

    public String getDiscom() {
        return discom;
    }

    public void setDiscom(String discom) {
        this.discom = discom;
    }

    public Integer getConsumptionMonth() {
        return consumptionMonth;
    }

    public void setConsumptionMonth(Integer consumptionMonth) {
        this.consumptionMonth = consumptionMonth;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StateTariff that = (StateTariff) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "StateTariff{" + "month=" + month + ", " + "state=" + state + ", " + "subCategory=" + subCategory + ", " + "id=" + id + ", " + "updatedAt=" + updatedAt + ", " + "baseEnergyRate=" + baseEnergyRate + ", " + "baseEnergyUnit=" + baseEnergyUnit + ", " + "consumerCategory=" + consumerCategory + ", " + "energyRate=" + energyRate + ", " + "supplyVoltage=" + supplyVoltage + ", " + "supplyVoltageCategory=" + supplyVoltageCategory + ", " + "todChargePercent=" + todChargePercent + ", " + "todEndTime=" + todEndTime + ", " + "todStartTime=" + todStartTime + ", " + "discom=" + discom + ", " + "consumptionMonth=" + consumptionMonth + "}";
    }
}
