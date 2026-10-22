package com.sense.prolt_operations.entity.forecasting;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public class ForecastConsumerDemandId implements Serializable {
    private static final long serialVersionUID = 1L;

    private String meterId;
    private LocalDateTime timestamp;

    public ForecastConsumerDemandId() {
    }

    public ForecastConsumerDemandId(String meterId, LocalDateTime timestamp) {
        this.meterId = meterId;
        this.timestamp = timestamp;
    }

    public String getMeterId() {
        return meterId;
    }

    public void setMeterId(String meterId) {
        this.meterId = meterId;
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
        ForecastConsumerDemandId that = (ForecastConsumerDemandId) o;
        return Objects.equals(meterId, that.meterId) && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(meterId, timestamp);
    }
}
