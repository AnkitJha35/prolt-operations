package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "UploadHistory", schema = "public")
public class UploadHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "datasetId")
    private String datasetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "market")
    private MarketType market;

    @Column(name = "deliveryDate")
    private LocalDate deliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    private UploadAction action;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @PrePersist
    public void prePersist() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    protected UploadHistory() {
    }

    public static UploadHistory create() {
        return new UploadHistory();
    }

    public UploadHistory(String id, String datasetId, MarketType market, LocalDate deliveryDate, UploadAction action) {
        this.id = id;
        this.datasetId = datasetId;
        this.market = market;
        this.deliveryDate = deliveryDate;
        this.action = action;
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

    public MarketType getMarket() {
        return market;
    }

    public void setMarket(MarketType market) {
        this.market = market;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public UploadAction getAction() {
        return action;
    }

    public void setAction(UploadAction action) {
        this.action = action;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UploadHistory that = (UploadHistory) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UploadHistory{" + "id=" + id + ", " + "datasetId=" + datasetId + ", " + "deliveryDate=" + deliveryDate + ", " + "timestamp=" + timestamp + "}";
    }
}
