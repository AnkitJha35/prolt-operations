package com.sense.prolt_operations.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimeseriesRecordDTO(
    int timeBlock,
    String time,
    Object mcp,
    Object mcv,
    Object purchaseBid,
    Object sellBid
) {
}
