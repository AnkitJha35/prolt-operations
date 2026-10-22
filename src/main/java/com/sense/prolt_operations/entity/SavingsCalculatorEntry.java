package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "savings_calculator_entries", schema = "public")
public class SavingsCalculatorEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "client_name")
    private String clientName;

    @Column(name = "industry_name")
    private String industryName;

    @Column(name = "address")
    private String address;

    @Column(name = "sanctioned_load_kw")
    private BigDecimal sanctionedLoadKw;

    @Column(name = "state_code")
    private String stateCode;

    @Column(name = "discom")
    private String discom;

    @Column(name = "consumer_category")
    private String consumerCategory;

    @Column(name = "voltage_level")
    private String voltageLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tod_consumptions")
    private String todConsumptions;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "prolt_margin")
    private BigDecimal proltMargin;

    @Column(name = "trader_margin")
    private BigDecimal traderMargin;

    @Column(name = "apply_electricity_duty")
    private Boolean applyElectricityDuty;

    @Column(name = "arrear_amount")
    private BigDecimal arrearAmount;

    @Column(name = "bill_date")
    private String billDate;

    @Column(name = "billed_demand_kv")
    private BigDecimal billedDemandKv;

    @Column(name = "consultancy_fee")
    private BigDecimal consultancyFee;

    @Column(name = "current_lpsc")
    private BigDecimal currentLpsc;

    @Column(name = "power_factor")
    private BigDecimal powerFactor;

    @Column(name = "probus_platform_fee")
    private BigDecimal probusPlatformFee;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "metering_charges")
    private BigDecimal meteringCharges;

    protected SavingsCalculatorEntry() {
    }

    public static SavingsCalculatorEntry create() {
        return new SavingsCalculatorEntry();
    }

    public SavingsCalculatorEntry(String id, String clientName, String industryName, String address, BigDecimal sanctionedLoadKw) {
        this.id = id;
        this.clientName = clientName;
        this.industryName = industryName;
        this.address = address;
        this.sanctionedLoadKw = sanctionedLoadKw;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getIndustryName() {
        return industryName;
    }

    public void setIndustryName(String industryName) {
        this.industryName = industryName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getSanctionedLoadKw() {
        return sanctionedLoadKw;
    }

    public void setSanctionedLoadKw(BigDecimal sanctionedLoadKw) {
        this.sanctionedLoadKw = sanctionedLoadKw;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getDiscom() {
        return discom;
    }

    public void setDiscom(String discom) {
        this.discom = discom;
    }

    public String getConsumerCategory() {
        return consumerCategory;
    }

    public void setConsumerCategory(String consumerCategory) {
        this.consumerCategory = consumerCategory;
    }

    public String getVoltageLevel() {
        return voltageLevel;
    }

    public void setVoltageLevel(String voltageLevel) {
        this.voltageLevel = voltageLevel;
    }

    public String getTodConsumptions() {
        return todConsumptions;
    }

    public void setTodConsumptions(String todConsumptions) {
        this.todConsumptions = todConsumptions;
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

    public BigDecimal getProltMargin() {
        return proltMargin;
    }

    public void setProltMargin(BigDecimal proltMargin) {
        this.proltMargin = proltMargin;
    }

    public BigDecimal getTraderMargin() {
        return traderMargin;
    }

    public void setTraderMargin(BigDecimal traderMargin) {
        this.traderMargin = traderMargin;
    }

    public Boolean getApplyElectricityDuty() {
        return applyElectricityDuty;
    }

    public void setApplyElectricityDuty(Boolean applyElectricityDuty) {
        this.applyElectricityDuty = applyElectricityDuty;
    }

    public BigDecimal getArrearAmount() {
        return arrearAmount;
    }

    public void setArrearAmount(BigDecimal arrearAmount) {
        this.arrearAmount = arrearAmount;
    }

    public String getBillDate() {
        return billDate;
    }

    public void setBillDate(String billDate) {
        this.billDate = billDate;
    }

    public BigDecimal getBilledDemandKv() {
        return billedDemandKv;
    }

    public void setBilledDemandKv(BigDecimal billedDemandKv) {
        this.billedDemandKv = billedDemandKv;
    }

    public BigDecimal getConsultancyFee() {
        return consultancyFee;
    }

    public void setConsultancyFee(BigDecimal consultancyFee) {
        this.consultancyFee = consultancyFee;
    }

    public BigDecimal getCurrentLpsc() {
        return currentLpsc;
    }

    public void setCurrentLpsc(BigDecimal currentLpsc) {
        this.currentLpsc = currentLpsc;
    }

    public BigDecimal getPowerFactor() {
        return powerFactor;
    }

    public void setPowerFactor(BigDecimal powerFactor) {
        this.powerFactor = powerFactor;
    }

    public BigDecimal getProbusPlatformFee() {
        return probusPlatformFee;
    }

    public void setProbusPlatformFee(BigDecimal probusPlatformFee) {
        this.probusPlatformFee = probusPlatformFee;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public BigDecimal getMeteringCharges() {
        return meteringCharges;
    }

    public void setMeteringCharges(BigDecimal meteringCharges) {
        this.meteringCharges = meteringCharges;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SavingsCalculatorEntry that = (SavingsCalculatorEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SavingsCalculatorEntry{" + "id=" + id + ", " + "clientName=" + clientName + ", " + "industryName=" + industryName + ", " + "address=" + address + ", " + "sanctionedLoadKw=" + sanctionedLoadKw + ", " + "stateCode=" + stateCode + ", " + "discom=" + discom + ", " + "consumerCategory=" + consumerCategory + ", " + "voltageLevel=" + voltageLevel + ", " + "todConsumptions=" + todConsumptions + ", " + "createdAt=" + createdAt + ", " + "updatedAt=" + updatedAt + ", " + "proltMargin=" + proltMargin + ", " + "traderMargin=" + traderMargin + ", " + "applyElectricityDuty=" + applyElectricityDuty + ", " + "arrearAmount=" + arrearAmount + ", " + "billDate=" + billDate + ", " + "billedDemandKv=" + billedDemandKv + ", " + "consultancyFee=" + consultancyFee + ", " + "currentLpsc=" + currentLpsc + ", " + "powerFactor=" + powerFactor + ", " + "probusPlatformFee=" + probusPlatformFee + ", " + "createdBy=" + createdBy + ", " + "updatedBy=" + updatedBy + ", " + "meteringCharges=" + meteringCharges + "}";
    }
}
