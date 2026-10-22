package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "GdamNewRecord", schema = "public")
public class GdamNewRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "datasetId")
    private String datasetId;

    @Column(name = "date")
    private String date;

    @Column(name = "intervalNumber")
    private Short intervalNumber;

    @Column(name = "intervalTime")
    private String intervalTime;

    @Column(name = "purchaseBid")
    private BigDecimal purchaseBid;

    @Column(name = "sellBidTotal")
    private BigDecimal sellBidTotal;

    @Column(name = "sellBidHydro")
    private BigDecimal sellBidHydro;

    @Column(name = "sellBidWind")
    private BigDecimal sellBidWind;

    @Column(name = "sellBidOtherRE")
    private BigDecimal sellBidOtherRE;

    @Column(name = "sellBidDRE")
    private BigDecimal sellBidDRE;

    @Column(name = "mcvTotal")
    private BigDecimal mcvTotal;

    @Column(name = "mcvHydro")
    private BigDecimal mcvHydro;

    @Column(name = "mcvWind")
    private BigDecimal mcvWind;

    @Column(name = "mcvOtherRE")
    private BigDecimal mcvOtherRE;

    @Column(name = "mcvDRE")
    private BigDecimal mcvDRE;

    @Column(name = "fsvTotal")
    private BigDecimal fsvTotal;

    @Column(name = "fsvHydro")
    private BigDecimal fsvHydro;

    @Column(name = "fsvWind")
    private BigDecimal fsvWind;

    @Column(name = "fsvOtherRE")
    private BigDecimal fsvOtherRE;

    @Column(name = "fsvDRE")
    private BigDecimal fsvDRE;

    @Column(name = "mcp")
    private BigDecimal mcp;

    protected GdamNewRecord() {
    }

    public static GdamNewRecord create() {
        return new GdamNewRecord();
    }

    public GdamNewRecord(String id, String datasetId, String date, Short intervalNumber, String intervalTime) {
        this.id = id;
        this.datasetId = datasetId;
        this.date = date;
        this.intervalNumber = intervalNumber;
        this.intervalTime = intervalTime;
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

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
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

    public BigDecimal getSellBidHydro() {
        return sellBidHydro;
    }

    public void setSellBidHydro(BigDecimal sellBidHydro) {
        this.sellBidHydro = sellBidHydro;
    }

    public BigDecimal getSellBidWind() {
        return sellBidWind;
    }

    public void setSellBidWind(BigDecimal sellBidWind) {
        this.sellBidWind = sellBidWind;
    }

    public BigDecimal getSellBidOtherRE() {
        return sellBidOtherRE;
    }

    public void setSellBidOtherRE(BigDecimal sellBidOtherRE) {
        this.sellBidOtherRE = sellBidOtherRE;
    }

    public BigDecimal getSellBidDRE() {
        return sellBidDRE;
    }

    public void setSellBidDRE(BigDecimal sellBidDRE) {
        this.sellBidDRE = sellBidDRE;
    }

    public BigDecimal getMcvTotal() {
        return mcvTotal;
    }

    public void setMcvTotal(BigDecimal mcvTotal) {
        this.mcvTotal = mcvTotal;
    }

    public BigDecimal getMcvHydro() {
        return mcvHydro;
    }

    public void setMcvHydro(BigDecimal mcvHydro) {
        this.mcvHydro = mcvHydro;
    }

    public BigDecimal getMcvWind() {
        return mcvWind;
    }

    public void setMcvWind(BigDecimal mcvWind) {
        this.mcvWind = mcvWind;
    }

    public BigDecimal getMcvOtherRE() {
        return mcvOtherRE;
    }

    public void setMcvOtherRE(BigDecimal mcvOtherRE) {
        this.mcvOtherRE = mcvOtherRE;
    }

    public BigDecimal getMcvDRE() {
        return mcvDRE;
    }

    public void setMcvDRE(BigDecimal mcvDRE) {
        this.mcvDRE = mcvDRE;
    }

    public BigDecimal getFsvTotal() {
        return fsvTotal;
    }

    public void setFsvTotal(BigDecimal fsvTotal) {
        this.fsvTotal = fsvTotal;
    }

    public BigDecimal getFsvHydro() {
        return fsvHydro;
    }

    public void setFsvHydro(BigDecimal fsvHydro) {
        this.fsvHydro = fsvHydro;
    }

    public BigDecimal getFsvWind() {
        return fsvWind;
    }

    public void setFsvWind(BigDecimal fsvWind) {
        this.fsvWind = fsvWind;
    }

    public BigDecimal getFsvOtherRE() {
        return fsvOtherRE;
    }

    public void setFsvOtherRE(BigDecimal fsvOtherRE) {
        this.fsvOtherRE = fsvOtherRE;
    }

    public BigDecimal getFsvDRE() {
        return fsvDRE;
    }

    public void setFsvDRE(BigDecimal fsvDRE) {
        this.fsvDRE = fsvDRE;
    }

    public BigDecimal getMcp() {
        return mcp;
    }

    public void setMcp(BigDecimal mcp) {
        this.mcp = mcp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GdamNewRecord that = (GdamNewRecord) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "GdamNewRecord{" + "id=" + id + ", " + "datasetId=" + datasetId + ", " + "date=" + date + ", " + "intervalNumber=" + intervalNumber + ", " + "intervalTime=" + intervalTime + ", " + "purchaseBid=" + purchaseBid + ", " + "sellBidTotal=" + sellBidTotal + ", " + "sellBidHydro=" + sellBidHydro + ", " + "sellBidWind=" + sellBidWind + ", " + "sellBidOtherRE=" + sellBidOtherRE + ", " + "sellBidDRE=" + sellBidDRE + ", " + "mcvTotal=" + mcvTotal + ", " + "mcvHydro=" + mcvHydro + ", " + "mcvWind=" + mcvWind + ", " + "mcvOtherRE=" + mcvOtherRE + ", " + "mcvDRE=" + mcvDRE + ", " + "fsvTotal=" + fsvTotal + ", " + "fsvHydro=" + fsvHydro + ", " + "fsvWind=" + fsvWind + ", " + "fsvOtherRE=" + fsvOtherRE + ", " + "fsvDRE=" + fsvDRE + ", " + "mcp=" + mcp + "}";
    }
}
