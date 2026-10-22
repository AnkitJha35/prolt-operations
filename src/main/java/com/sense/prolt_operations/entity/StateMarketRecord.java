package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "StateMarketRecord", schema = "public")
public class StateMarketRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "market")
    private MarketType market;

    @Column(name = "area")
    private String area;

    @Column(name = "deliveryDate")
    private LocalDate deliveryDate;

    @Column(name = "intervalNumber")
    private Short intervalNumber;

    @Column(name = "intervalTime")
    private String intervalTime;

    @Column(name = "purchaseBid")
    private BigDecimal purchaseBid;

    @Column(name = "sellBid")
    private BigDecimal sellBid;

    @Column(name = "clearedVolume")
    private BigDecimal clearedVolume;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    protected StateMarketRecord() {
    }

    public static StateMarketRecord create() {
        return new StateMarketRecord();
    }

    public StateMarketRecord(String id, MarketType market, String area, LocalDate deliveryDate, Short intervalNumber) {
        this.id = id;
        this.market = market;
        this.area = area;
        this.deliveryDate = deliveryDate;
        this.intervalNumber = intervalNumber;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public MarketType getMarket() {
        return market;
    }

    public void setMarket(MarketType market) {
        this.market = market;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
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

    public BigDecimal getClearedVolume() {
        return clearedVolume;
    }

    public void setClearedVolume(BigDecimal clearedVolume) {
        this.clearedVolume = clearedVolume;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StateMarketRecord that = (StateMarketRecord) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "StateMarketRecord{" + "id=" + id + ", " + "area=" + area + ", " + "deliveryDate=" + deliveryDate + ", " + "intervalNumber=" + intervalNumber + ", " + "intervalTime=" + intervalTime + ", " + "purchaseBid=" + purchaseBid + ", " + "sellBid=" + sellBid + ", " + "clearedVolume=" + clearedVolume + ", " + "price=" + price + ", " + "createdAt=" + createdAt + ", " + "updatedAt=" + updatedAt + "}";
    }
}
