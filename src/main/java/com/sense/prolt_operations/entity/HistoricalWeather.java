package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "historical_weather", schema = "public")
public class HistoricalWeather implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "timestamp")
    private OffsetDateTime timestamp;

    @Column(name = "interval_number")
    private Integer intervalNumber;

    @Column(name = "temperature")
    private BigDecimal temperature;

    @Column(name = "humidity")
    private BigDecimal humidity;

    @Column(name = "wind_speed")
    private BigDecimal windSpeed;

    @Column(name = "solar_radiation")
    private BigDecimal solarRadiation;

    @Column(name = "precipitation")
    private BigDecimal precipitation;

    @Column(name = "cloud_cover")
    private BigDecimal cloudCover;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected HistoricalWeather() {
    }

    public static HistoricalWeather create() {
        return new HistoricalWeather();
    }

    public HistoricalWeather(Integer id, OffsetDateTime timestamp, Integer intervalNumber, BigDecimal temperature, BigDecimal humidity) {
        this.id = id;
        this.timestamp = timestamp;
        this.intervalNumber = intervalNumber;
        this.temperature = temperature;
        this.humidity = humidity;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getIntervalNumber() {
        return intervalNumber;
    }

    public void setIntervalNumber(Integer intervalNumber) {
        this.intervalNumber = intervalNumber;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public BigDecimal getHumidity() {
        return humidity;
    }

    public void setHumidity(BigDecimal humidity) {
        this.humidity = humidity;
    }

    public BigDecimal getWindSpeed() {
        return windSpeed;
    }

    public void setWindSpeed(BigDecimal windSpeed) {
        this.windSpeed = windSpeed;
    }

    public BigDecimal getSolarRadiation() {
        return solarRadiation;
    }

    public void setSolarRadiation(BigDecimal solarRadiation) {
        this.solarRadiation = solarRadiation;
    }

    public BigDecimal getPrecipitation() {
        return precipitation;
    }

    public void setPrecipitation(BigDecimal precipitation) {
        this.precipitation = precipitation;
    }

    public BigDecimal getCloudCover() {
        return cloudCover;
    }

    public void setCloudCover(BigDecimal cloudCover) {
        this.cloudCover = cloudCover;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoricalWeather that = (HistoricalWeather) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "HistoricalWeather{" + "id=" + id + ", " + "timestamp=" + timestamp + ", " + "intervalNumber=" + intervalNumber + ", " + "temperature=" + temperature + ", " + "humidity=" + humidity + ", " + "windSpeed=" + windSpeed + ", " + "solarRadiation=" + solarRadiation + ", " + "precipitation=" + precipitation + ", " + "cloudCover=" + cloudCover + ", " + "createdAt=" + createdAt + "}";
    }
}
