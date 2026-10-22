package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "discom_list", schema = "public")
public class DiscomList implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "code")
    private String code;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "state_code")
    private String stateCode;

    @Column(name = "discom_type")
    private String discomType;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected DiscomList() {
    }

    public static DiscomList create() {
        return new DiscomList();
    }

    public DiscomList(String code, String legalName, String stateCode, String discomType, Integer id) {
        this.code = code;
        this.legalName = legalName;
        this.stateCode = stateCode;
        this.discomType = discomType;
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getDiscomType() {
        return discomType;
    }

    public void setDiscomType(String discomType) {
        this.discomType = discomType;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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
        DiscomList that = (DiscomList) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DiscomList{" + "code=" + code + ", " + "legalName=" + legalName + ", " + "stateCode=" + stateCode + ", " + "discomType=" + discomType + ", " + "id=" + id + ", " + "updatedAt=" + updatedAt + "}";
    }
}
