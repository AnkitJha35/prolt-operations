package com.sense.prolt_operations.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.sense.prolt_operations.entity.MarketType;
import com.sense.prolt_operations.entity.UploadAction;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UploadHistoryDTO(
    String id,
    String datasetId,
    MarketType market,
    LocalDate deliveryDate,
    UploadAction action,
    LocalDateTime timestamp
) {
    public static UploadHistoryDTO fromEntity(com.sense.prolt_operations.entity.UploadHistory entity) {
        if (entity == null) {
            return null;
        }
        return new UploadHistoryDTO(
            entity.getId(),
            entity.getDatasetId(),
            entity.getMarket(),
            entity.getDeliveryDate(),
            entity.getAction(),
            entity.getTimestamp()
        );
    }
}
