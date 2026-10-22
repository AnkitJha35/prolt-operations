package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "region_state", schema = "public")
public class RegionState implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "regional_grid")
    private String regionalGrid;

    @Column(name = "region_code")
    private String regionCode;

    @Column(name = "region_name")
    private String regionName;

    @Column(name = "state_name")
    private String stateName;

    @Column(name = "state_code")
    private String stateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "state_or_ut")
    private StateUtEnum stateOrUt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected RegionState() {
    }

    public static RegionState create() {
        return new RegionState();
    }

    public RegionState(Integer id, String regionalGrid, String regionCode, String regionName, String stateName) {
        this.id = id;
        this.regionalGrid = regionalGrid;
        this.regionCode = regionCode;
        this.regionName = regionName;
        this.stateName = stateName;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getRegionalGrid() {
        return regionalGrid;
    }

    public void setRegionalGrid(String regionalGrid) {
        this.regionalGrid = regionalGrid;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public StateUtEnum getStateOrUt() {
        return stateOrUt;
    }

    public void setStateOrUt(StateUtEnum stateOrUt) {
        this.stateOrUt = stateOrUt;
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
        RegionState that = (RegionState) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RegionState{" + "id=" + id + ", " + "regionalGrid=" + regionalGrid + ", " + "regionCode=" + regionCode + ", " + "regionName=" + regionName + ", " + "stateName=" + stateName + ", " + "stateCode=" + stateCode + ", " + "updatedAt=" + updatedAt + "}";
    }
}
