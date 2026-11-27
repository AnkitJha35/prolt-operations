package com.sense.prolt_operations.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.sense.prolt_operations.entity.DatasetStatus;
import com.sense.prolt_operations.entity.MarketType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatasetDTO(
    String id,
    MarketType market,
    LocalDate deliveryDate,
    DatasetStatus status,
    String fileName,
    LocalDateTime uploadedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static DatasetDTO fromEntity(com.sense.prolt_operations.entity.Dataset entity) {
        if (entity == null) {
            return null;
        }
        return new DatasetDTO(
            entity.getId(),
            entity.getMarket(),
            entity.getDeliveryDate(),
            entity.getStatus(),
            entity.getFileName(),
            entity.getUploadedAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
