package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "RtmRecord", schema = "public")
public class RtmRecord implements Serializable {

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

    @Column(name = "sessionId")
    private String sessionId;

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

    protected RtmRecord() {
    }

    public static RtmRecord create() {
        return new RtmRecord();
    }

    public RtmRecord(String id, String datasetId, Short intervalNumber, String intervalTime, String sessionId) {
        this.id = id;
        this.datasetId = datasetId;
        this.intervalNumber = intervalNumber;
        this.intervalTime = intervalTime;
        this.sessionId = sessionId;
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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
        RtmRecord that = (RtmRecord) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RtmRecord{" + "id=" + id + ", " + "datasetId=" + datasetId + ", " + "intervalNumber=" + intervalNumber + ", " + "intervalTime=" + intervalTime + ", " + "sessionId=" + sessionId + ", " + "purchaseBid=" + purchaseBid + ", " + "sellBid=" + sellBid + ", " + "mcv=" + mcv + ", " + "fsv=" + fsv + ", " + "mcp=" + mcp + ", " + "date=" + date + "}";
    }
}
