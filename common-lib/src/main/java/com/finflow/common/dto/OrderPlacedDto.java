package com.finflow.common.dto;

import com.finflow.common.domain.OrderSide;
import com.finflow.common.domain.OrderType;
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
public class OrderPlacedDto {

    private UUID orderId;
    private UUID traderId;
    private String symbol;
    private OrderSide side;
    private OrderType type;
    private BigDecimal price;
    private BigDecimal quantity;
    private Instant placedAt;
}
