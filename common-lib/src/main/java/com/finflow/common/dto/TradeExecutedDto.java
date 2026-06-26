package com.finflow.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeExecutedDto {

    private UUID tradeId;
    private UUID buyOrderId;
    private UUID sellOrderId;
    private String symbol;
    private BigDecimal price;
    private BigDecimal quantity;
    private Instant executedAt;
}
