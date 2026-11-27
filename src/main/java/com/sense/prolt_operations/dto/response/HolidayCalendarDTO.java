package com.sense.prolt_operations.dto.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sense.prolt_operations.entity.HolidayCalendar;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HolidayCalendarDTO(
    String id,
    String month,
    String holidayDate,
    String holidayName,
    String holidayType,
    String state,
    Boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static HolidayCalendarDTO fromEntity(HolidayCalendar entity) {
        if (entity == null) return null;
        return new HolidayCalendarDTO(
            entity.getId(),
            entity.getMonth(),
            entity.getHolidayDate(),
            entity.getHolidayName(),
            entity.getHolidayType(),
            entity.getState(),
            entity.getIsActive(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
