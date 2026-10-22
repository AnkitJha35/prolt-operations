package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "NppRawDemandData", schema = "public")
public class NppRawDemandData implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "date")
    private String date;

    @Column(name = "timeStr")
    private String timeStr;

    @Column(name = "demandMet")
    private Double demandMet;

    @Column(name = "dataUpdatedAt")
    private String dataUpdatedAt;

    @Column(name = "fetchedAt")
    private LocalDateTime fetchedAt;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    protected NppRawDemandData() {
    }

    public static NppRawDemandData create() {
        return new NppRawDemandData();
    }

    public NppRawDemandData(String id, String date, String timeStr, Double demandMet, String dataUpdatedAt) {
        this.id = id;
        this.date = date;
        this.timeStr = timeStr;
        this.demandMet = demandMet;
        this.dataUpdatedAt = dataUpdatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTimeStr() {
        return timeStr;
    }

    public void setTimeStr(String timeStr) {
        this.timeStr = timeStr;
    }

    public Double getDemandMet() {
        return demandMet;
    }

    public void setDemandMet(Double demandMet) {
        this.demandMet = demandMet;
    }

    public String getDataUpdatedAt() {
        return dataUpdatedAt;
    }

    public void setDataUpdatedAt(String dataUpdatedAt) {
        this.dataUpdatedAt = dataUpdatedAt;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
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
        NppRawDemandData that = (NppRawDemandData) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "NppRawDemandData{" + "id=" + id + ", " + "date=" + date + ", " + "timeStr=" + timeStr + ", " + "demandMet=" + demandMet + ", " + "dataUpdatedAt=" + dataUpdatedAt + ", " + "fetchedAt=" + fetchedAt + ", " + "createdAt=" + createdAt + ", " + "updatedAt=" + updatedAt + "}";
    }
}
