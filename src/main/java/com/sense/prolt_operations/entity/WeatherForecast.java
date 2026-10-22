package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "WeatherForecast", schema = "public")
public class WeatherForecast implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "date")
    private String date;

    @Column(name = "maxTemp")
    private Double maxTemp;

    @Column(name = "minTemp")
    private Double minTemp;

    @Column(name = "windSpeed")
    private Double windSpeed;

    @Column(name = "relativeHumidity")
    private Double relativeHumidity;

    @Column(name = "precipitationProb")
    private Double precipitationProb;

    @Column(name = "precipitationSum")
    private Double precipitationSum;

    @Column(name = "sunshineDuration")
    private Double sunshineDuration;

    @Column(name = "sunrise")
    private String sunrise;

    @Column(name = "sunset")
    private String sunset;

    @Column(name = "isActual")
    private Boolean isActual;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "cityId")
    private String cityId;

    @Column(name = "rainSum")
    private Double rainSum;

    protected WeatherForecast() {
    }

    public static WeatherForecast create() {
        return new WeatherForecast();
    }

    public WeatherForecast(String id, String date, Double maxTemp, Double minTemp, Double windSpeed) {
        this.id = id;
        this.date = date;
        this.maxTemp = maxTemp;
        this.minTemp = minTemp;
        this.windSpeed = windSpeed;
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

    public Double getMaxTemp() {
        return maxTemp;
    }

    public void setMaxTemp(Double maxTemp) {
        this.maxTemp = maxTemp;
    }

    public Double getMinTemp() {
        return minTemp;
    }

    public void setMinTemp(Double minTemp) {
        this.minTemp = minTemp;
    }

    public Double getWindSpeed() {
        return windSpeed;
    }

    public void setWindSpeed(Double windSpeed) {
        this.windSpeed = windSpeed;
    }

    public Double getRelativeHumidity() {
        return relativeHumidity;
    }

    public void setRelativeHumidity(Double relativeHumidity) {
        this.relativeHumidity = relativeHumidity;
    }

    public Double getPrecipitationProb() {
        return precipitationProb;
    }

    public void setPrecipitationProb(Double precipitationProb) {
        this.precipitationProb = precipitationProb;
    }

    public Double getPrecipitationSum() {
        return precipitationSum;
    }

    public void setPrecipitationSum(Double precipitationSum) {
        this.precipitationSum = precipitationSum;
    }

    public Double getSunshineDuration() {
        return sunshineDuration;
    }

    public void setSunshineDuration(Double sunshineDuration) {
        this.sunshineDuration = sunshineDuration;
    }

    public String getSunrise() {
        return sunrise;
    }

    public void setSunrise(String sunrise) {
        this.sunrise = sunrise;
    }

    public String getSunset() {
        return sunset;
    }

    public void setSunset(String sunset) {
        this.sunset = sunset;
    }

    public Boolean getIsActual() {
        return isActual;
    }

    public void setIsActual(Boolean isActual) {
        this.isActual = isActual;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCityId() {
        return cityId;
    }

    public void setCityId(String cityId) {
        this.cityId = cityId;
    }

    public Double getRainSum() {
        return rainSum;
    }

    public void setRainSum(Double rainSum) {
        this.rainSum = rainSum;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WeatherForecast that = (WeatherForecast) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "WeatherForecast{" + "id=" + id + ", " + "date=" + date + ", " + "maxTemp=" + maxTemp + ", " + "minTemp=" + minTemp + ", " + "windSpeed=" + windSpeed + ", " + "relativeHumidity=" + relativeHumidity + ", " + "precipitationProb=" + precipitationProb + ", " + "precipitationSum=" + precipitationSum + ", " + "sunshineDuration=" + sunshineDuration + ", " + "sunrise=" + sunrise + ", " + "sunset=" + sunset + ", " + "isActual=" + isActual + ", " + "updatedAt=" + updatedAt + ", " + "cityId=" + cityId + ", " + "rainSum=" + rainSum + "}";
    }
}
