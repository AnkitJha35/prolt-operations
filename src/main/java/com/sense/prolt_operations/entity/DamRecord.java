package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "DamRecord", schema = "public")
public class DamRecord implements Serializable {

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

    @Column(name = "sellBid")
    private BigDecimal sellBid;

    @Column(name = "mcv")
    private BigDecimal mcv;

    @Column(name = "fsv")
    private BigDecimal fsv;

    @Column(name = "mcp")
    private BigDecimal mcp;

    @Column(name = "date")
    private String date;

    protected DamRecord() {
    }

    public static DamRecord create() {
        return new DamRecord();
    }

    public DamRecord(String id, String datasetId, Short intervalNumber, String intervalTime, BigDecimal purchaseBid) {
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

    public BigDecimal getSellBid() {
        return sellBid;
    }

    public void setSellBid(BigDecimal sellBid) {
        this.sellBid = sellBid;
    }

    public BigDecimal getMcv() {
        return mcv;
    }

    public void setMcv(BigDecimal mcv) {
        this.mcv = mcv;
    }

    public BigDecimal getFsv() {
        return fsv;
    }

    public void setFsv(BigDecimal fsv) {
        this.fsv = fsv;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DamRecord that = (DamRecord) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DamRecord{" + "id=" + id + ", " + "datasetId=" + datasetId + ", " + "intervalNumber=" + intervalNumber + ", " + "intervalTime=" + intervalTime + ", " + "purchaseBid=" + purchaseBid + ", " + "sellBid=" + sellBid + ", " + "mcv=" + mcv + ", " + "fsv=" + fsv + ", " + "mcp=" + mcp + ", " + "date=" + date + "}";
    }
}
