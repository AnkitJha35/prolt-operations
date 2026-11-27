package com.sense.prolt_operations.dto.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimeseriesResponse(
    boolean success,
    List<TimeseriesRecordDTO> data,
    int count
) {
}
