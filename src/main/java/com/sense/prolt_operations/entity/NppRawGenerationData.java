package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "NppRawGenerationData", schema = "public")
public class NppRawGenerationData implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "date")
    private String date;

    @Column(name = "timeStr")
    private String timeStr;

    @Column(name = "thermal")
    private Double thermal;

    @Column(name = "gas")
    private Double gas;

    @Column(name = "nuclear")
    private Double nuclear;

    @Column(name = "hydro")
    private Double hydro;

    @Column(name = "wind")
    private Double wind;

    @Column(name = "solar")
    private Double solar;

    @Column(name = "dataUpdatedAt")
    private String dataUpdatedAt;

    @Column(name = "fetchedAt")
    private LocalDateTime fetchedAt;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    protected NppRawGenerationData() {
    }

    public static NppRawGenerationData create() {
        return new NppRawGenerationData();
    }

    public NppRawGenerationData(String id, String date, String timeStr, Double thermal, Double gas) {
        this.id = id;
        this.date = date;
        this.timeStr = timeStr;
        this.thermal = thermal;
        this.gas = gas;
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

    public Double getThermal() {
        return thermal;
    }

    public void setThermal(Double thermal) {
        this.thermal = thermal;
    }

    public Double getGas() {
        return gas;
    }

    public void setGas(Double gas) {
        this.gas = gas;
    }

    public Double getNuclear() {
        return nuclear;
    }

    public void setNuclear(Double nuclear) {
        this.nuclear = nuclear;
    }

    public Double getHydro() {
        return hydro;
    }

    public void setHydro(Double hydro) {
        this.hydro = hydro;
    }

    public Double getWind() {
        return wind;
    }

    public void setWind(Double wind) {
        this.wind = wind;
    }

    public Double getSolar() {
        return solar;
    }

    public void setSolar(Double solar) {
        this.solar = solar;
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
        NppRawGenerationData that = (NppRawGenerationData) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "NppRawGenerationData{" + "id=" + id + ", " + "date=" + date + ", " + "timeStr=" + timeStr + ", " + "thermal=" + thermal + ", " + "gas=" + gas + ", " + "nuclear=" + nuclear + ", " + "hydro=" + hydro + ", " + "wind=" + wind + ", " + "solar=" + solar + ", " + "dataUpdatedAt=" + dataUpdatedAt + ", " + "fetchedAt=" + fetchedAt + ", " + "createdAt=" + createdAt + ", " + "updatedAt=" + updatedAt + "}";
    }
}
