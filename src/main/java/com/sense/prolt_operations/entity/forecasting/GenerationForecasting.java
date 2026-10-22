package com.sense.prolt_operations.entity.forecasting;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "Generation_forecasting", schema = "forecasting")
public class GenerationForecasting implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "forecast_generated_at")
    private LocalDateTime forecastGeneratedAt;

    @Column(name = "forecast_for_timestamp")
    private LocalDateTime forecastForTimestamp;

    @Column(name = "source")
    private String source;

    @Column(name = "forecast_mw")
    private Double forecastMw;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "model_version")
    private String modelVersion;

    protected GenerationForecasting() {
    }

    public static GenerationForecasting create() {
        return new GenerationForecasting();
    }

    public GenerationForecasting(Integer id, LocalDateTime forecastGeneratedAt, LocalDateTime forecastForTimestamp, String source, Double forecastMw) {
        this.id = id;
        this.forecastGeneratedAt = forecastGeneratedAt;
        this.forecastForTimestamp = forecastForTimestamp;
        this.source = source;
        this.forecastMw = forecastMw;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDateTime getForecastGeneratedAt() {
        return forecastGeneratedAt;
    }

    public void setForecastGeneratedAt(LocalDateTime forecastGeneratedAt) {
        this.forecastGeneratedAt = forecastGeneratedAt;
    }

    public LocalDateTime getForecastForTimestamp() {
        return forecastForTimestamp;
    }

    public void setForecastForTimestamp(LocalDateTime forecastForTimestamp) {
        this.forecastForTimestamp = forecastForTimestamp;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Double getForecastMw() {
        return forecastMw;
    }

    public void setForecastMw(Double forecastMw) {
        this.forecastMw = forecastMw;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GenerationForecasting that = (GenerationForecasting) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "GenerationForecasting{" + "id=" + id + ", " + "forecastGeneratedAt=" + forecastGeneratedAt + ", " + "forecastForTimestamp=" + forecastForTimestamp + ", " + "source=" + source + ", " + "forecastMw=" + forecastMw + ", " + "modelName=" + modelName + ", " + "modelVersion=" + modelVersion + "}";
    }
}
