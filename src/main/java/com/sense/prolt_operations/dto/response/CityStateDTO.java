package com.sense.prolt_operations.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sense.prolt_operations.entity.CityStateData;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CityStateDTO(
    String id,
    String cityName,
    String stateName,
    Integer population,
    Double latitude,
    Double longitude,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static CityStateDTO fromEntity(CityStateData entity) {
        if (entity == null) return null;
        return new CityStateDTO(
            entity.getId(),
            entity.getCityName(),
            entity.getStateName(),
            entity.getPopulation(),
            entity.getLatitude(),
            entity.getLongitude(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
