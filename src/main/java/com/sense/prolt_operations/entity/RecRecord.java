package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "RecRecord", schema = "public")
public class RecRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "datasetId")
    private String datasetId;

    @Column(name = "buyBids")
    private BigDecimal buyBids;

    @Column(name = "clearedPrice")
    private BigDecimal clearedPrice;

    @Column(name = "clearedVolume")
    private BigDecimal clearedVolume;

    @Column(name = "month")
    private String month;

    @Column(name = "noOfParticipants")
    private Integer noOfParticipants;

    @Column(name = "sellBids")
    private BigDecimal sellBids;

    @Column(name = "type")
    private String type;

    @Column(name = "year")
    private Short year;

    protected RecRecord() {
    }

    public static RecRecord create() {
        return new RecRecord();
    }

    public RecRecord(String id, String datasetId, BigDecimal buyBids, BigDecimal clearedPrice, BigDecimal clearedVolume) {
        this.id = id;
        this.datasetId = datasetId;
        this.buyBids = buyBids;
        this.clearedPrice = clearedPrice;
        this.clearedVolume = clearedVolume;
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

    public BigDecimal getBuyBids() {
        return buyBids;
    }

    public void setBuyBids(BigDecimal buyBids) {
        this.buyBids = buyBids;
    }

    public BigDecimal getClearedPrice() {
        return clearedPrice;
    }

    public void setClearedPrice(BigDecimal clearedPrice) {
        this.clearedPrice = clearedPrice;
    }

    public BigDecimal getClearedVolume() {
        return clearedVolume;
    }

    public void setClearedVolume(BigDecimal clearedVolume) {
        this.clearedVolume = clearedVolume;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public Integer getNoOfParticipants() {
        return noOfParticipants;
    }

    public void setNoOfParticipants(Integer noOfParticipants) {
        this.noOfParticipants = noOfParticipants;
    }

    public BigDecimal getSellBids() {
        return sellBids;
    }

    public void setSellBids(BigDecimal sellBids) {
        this.sellBids = sellBids;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Short getYear() {
        return year;
    }

    public void setYear(Short year) {
        this.year = year;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecRecord that = (RecRecord) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RecRecord{" + "id=" + id + ", " + "datasetId=" + datasetId + ", " + "buyBids=" + buyBids + ", " + "clearedPrice=" + clearedPrice + ", " + "clearedVolume=" + clearedVolume + ", " + "month=" + month + ", " + "noOfParticipants=" + noOfParticipants + ", " + "sellBids=" + sellBids + ", " + "type=" + type + ", " + "year=" + year + "}";
    }
}
