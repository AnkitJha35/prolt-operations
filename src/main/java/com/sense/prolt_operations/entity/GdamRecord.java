package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "GdamRecord", schema = "public")
public class GdamRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "datasetId")
    private String datasetId;

    @Column(name = "intervalNumber")
    private Short intervalNumber;

    @Column(name = "intervalTime")
    private String intervalTime;

    @Column(name = "purchaseBid")
    private BigDecimal purchaseBid;

    @Column(name = "sellBidTotal")
    private BigDecimal sellBidTotal;

    @Column(name = "sellBidSolar")
    private BigDecimal sellBidSolar;

    @Column(name = "sellBidNonSolar")
    private BigDecimal sellBidNonSolar;

    @Column(name = "sellBidHydro")
    private BigDecimal sellBidHydro;

    @Column(name = "mcvTotal")
    private BigDecimal mcvTotal;

    @Column(name = "mcvSolar")
    private BigDecimal mcvSolar;

    @Column(name = "mcvNonSolar")
    private BigDecimal mcvNonSolar;

    @Column(name = "mcvHydro")
    private BigDecimal mcvHydro;

    @Column(name = "fsvTotal")
    private BigDecimal fsvTotal;

    @Column(name = "fsvSolar")
    private BigDecimal fsvSolar;

    @Column(name = "fsvNonSolar")
    private BigDecimal fsvNonSolar;

    @Column(name = "fsvHydro")
    private BigDecimal fsvHydro;

    @Column(name = "mcp")
    private BigDecimal mcp;

    @Column(name = "date")
    private String date;

    @Column(name = "fsvORE")
    private BigDecimal fsvORE;

    @Column(name = "fsvOtherRE")
    private BigDecimal fsvOtherRE;

    @Column(name = "fsvWind")
    private BigDecimal fsvWind;

    @Column(name = "mcvORE")
    private BigDecimal mcvORE;

    @Column(name = "mcvOtherRE")
    private BigDecimal mcvOtherRE;

    @Column(name = "mcvWind")
    private BigDecimal mcvWind;

    @Column(name = "sellBidORE")
    private BigDecimal sellBidORE;

    @Column(name = "sellBidOtherRE")
    private BigDecimal sellBidOtherRE;

    @Column(name = "sellBidWind")
    private BigDecimal sellBidWind;

    protected GdamRecord() {
    }

    public static GdamRecord create() {
        return new GdamRecord();
    }

    public GdamRecord(String id, String datasetId, Short intervalNumber, String intervalTime, BigDecimal purchaseBid) {
        this.id = id;
        this.datasetId = datasetId;
        this.intervalNumber = intervalNumber;
        this.intervalTime = intervalTime;
        this.purchaseBid = purchaseBid;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public Short getIntervalNumber() {
        return intervalNumber;
    }

    public void setIntervalNumber(Short intervalNumber) {
        this.intervalNumber = intervalNumber;
    }

    public String getIntervalTime() {
        return intervalTime;
    }

    public void setIntervalTime(String intervalTime) {
        this.intervalTime = intervalTime;
    }

    public BigDecimal getPurchaseBid() {
        return purchaseBid;
    }

    public void setPurchaseBid(BigDecimal purchaseBid) {
        this.purchaseBid = purchaseBid;
    }

    public BigDecimal getSellBidTotal() {
        return sellBidTotal;
    }

    public void setSellBidTotal(BigDecimal sellBidTotal) {
        this.sellBidTotal = sellBidTotal;
    }

    public BigDecimal getSellBidSolar() {
        return sellBidSolar;
    }

    public void setSellBidSolar(BigDecimal sellBidSolar) {
        this.sellBidSolar = sellBidSolar;
    }

    public BigDecimal getSellBidNonSolar() {
        return sellBidNonSolar;
    }

    public void setSellBidNonSolar(BigDecimal sellBidNonSolar) {
        this.sellBidNonSolar = sellBidNonSolar;
    }

    public BigDecimal getSellBidHydro() {
        return sellBidHydro;
    }

    public void setSellBidHydro(BigDecimal sellBidHydro) {
        this.sellBidHydro = sellBidHydro;
    }

    public BigDecimal getMcvTotal() {
        return mcvTotal;
    }

    public void setMcvTotal(BigDecimal mcvTotal) {
        this.mcvTotal = mcvTotal;
    }

    public BigDecimal getMcvSolar() {
        return mcvSolar;
    }

    public void setMcvSolar(BigDecimal mcvSolar) {
        this.mcvSolar = mcvSolar;
    }

    public BigDecimal getMcvNonSolar() {
        return mcvNonSolar;
    }

    public void setMcvNonSolar(BigDecimal mcvNonSolar) {
        this.mcvNonSolar = mcvNonSolar;
    }

    public BigDecimal getMcvHydro() {
        return mcvHydro;
    }

    public void setMcvHydro(BigDecimal mcvHydro) {
        this.mcvHydro = mcvHydro;
    }

    public BigDecimal getFsvTotal() {
        return fsvTotal;
    }

    public void setFsvTotal(BigDecimal fsvTotal) {
        this.fsvTotal = fsvTotal;
    }

    public BigDecimal getFsvSolar() {
        return fsvSolar;
    }

    public void setFsvSolar(BigDecimal fsvSolar) {
        this.fsvSolar = fsvSolar;
    }

    public BigDecimal getFsvNonSolar() {
        return fsvNonSolar;
    }

    public void setFsvNonSolar(BigDecimal fsvNonSolar) {
        this.fsvNonSolar = fsvNonSolar;
    }

    public BigDecimal getFsvHydro() {
        return fsvHydro;
    }

    public void setFsvHydro(BigDecimal fsvHydro) {
        this.fsvHydro = fsvHydro;
    }

    public BigDecimal getMcp() {
        return mcp;
    }

    public void setMcp(BigDecimal mcp) {
        this.mcp = mcp;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public BigDecimal getFsvORE() {
        return fsvORE;
    }

    public void setFsvORE(BigDecimal fsvORE) {
        this.fsvORE = fsvORE;
    }

    public BigDecimal getFsvOtherRE() {
        return fsvOtherRE;
    }

    public void setFsvOtherRE(BigDecimal fsvOtherRE) {
        this.fsvOtherRE = fsvOtherRE;
    }

    public BigDecimal getFsvWind() {
        return fsvWind;
    }

    public void setFsvWind(BigDecimal fsvWind) {
        this.fsvWind = fsvWind;
    }

    public BigDecimal getMcvORE() {
        return mcvORE;
    }

    public void setMcvORE(BigDecimal mcvORE) {
        this.mcvORE = mcvORE;
    }

    public BigDecimal getMcvOtherRE() {
        return mcvOtherRE;
    }

    public void setMcvOtherRE(BigDecimal mcvOtherRE) {
        this.mcvOtherRE = mcvOtherRE;
    }

    public BigDecimal getMcvWind() {
        return mcvWind;
    }

    public void setMcvWind(BigDecimal mcvWind) {
        this.mcvWind = mcvWind;
    }

    public BigDecimal getSellBidORE() {
        return sellBidORE;
    }

    public void setSellBidORE(BigDecimal sellBidORE) {
        this.sellBidORE = sellBidORE;
    }

    public BigDecimal getSellBidOtherRE() {
        return sellBidOtherRE;
    }

    public void setSellBidOtherRE(BigDecimal sellBidOtherRE) {
        this.sellBidOtherRE = sellBidOtherRE;
    }

    public BigDecimal getSellBidWind() {
        return sellBidWind;
    }

    public void setSellBidWind(BigDecimal sellBidWind) {
        this.sellBidWind = sellBidWind;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GdamRecord that = (GdamRecord) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "GdamRecord{" + "id=" + id + ", " + "datasetId=" + datasetId + ", " + "intervalNumber=" + intervalNumber + ", " + "intervalTime=" + intervalTime + ", " + "purchaseBid=" + purchaseBid + ", " + "sellBidTotal=" + sellBidTotal + ", " + "sellBidSolar=" + sellBidSolar + ", " + "sellBidNonSolar=" + sellBidNonSolar + ", " + "sellBidHydro=" + sellBidHydro + ", " + "mcvTotal=" + mcvTotal + ", " + "mcvSolar=" + mcvSolar + ", " + "mcvNonSolar=" + mcvNonSolar + ", " + "mcvHydro=" + mcvHydro + ", " + "fsvTotal=" + fsvTotal + ", " + "fsvSolar=" + fsvSolar + ", " + "fsvNonSolar=" + fsvNonSolar + ", " + "fsvHydro=" + fsvHydro + ", " + "mcp=" + mcp + ", " + "date=" + date + ", " + "fsvORE=" + fsvORE + ", " + "fsvOtherRE=" + fsvOtherRE + ", " + "fsvWind=" + fsvWind + ", " + "mcvORE=" + mcvORE + ", " + "mcvOtherRE=" + mcvOtherRE + ", " + "mcvWind=" + mcvWind + ", " + "sellBidORE=" + sellBidORE + ", " + "sellBidOtherRE=" + sellBidOtherRE + ", " + "sellBidWind=" + sellBidWind + "}";
    }
}
