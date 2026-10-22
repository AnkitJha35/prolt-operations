package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "ApiLog", schema = "public")
public class ApiLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "apiName")
    private String apiName;

    @Column(name = "endpoint")
    private String endpoint;

    @Column(name = "status")
    private String status;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "details")
    private String details;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    protected ApiLog() {
    }

    public static ApiLog create() {
        return new ApiLog();
    }

    public ApiLog(String id, String apiName, String endpoint, String status, LocalDateTime createdAt) {
        this.id = id;
        this.apiName = apiName;
        this.endpoint = endpoint;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiLog that = (ApiLog) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ApiLog{" + "id=" + id + ", " + "apiName=" + apiName + ", " + "endpoint=" + endpoint + ", " + "status=" + status + ", " + "createdAt=" + createdAt + ", " + "details=" + details + "}";
    }
}
