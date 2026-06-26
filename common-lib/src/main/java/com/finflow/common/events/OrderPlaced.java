package com.finflow.common.events;

import com.finflow.common.domain.OrderSide;
import com.finflow.common.domain.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderPlaced(
        UUID orderId,
        UUID traderId,
        String symbol,
        OrderSide side,
        OrderType type,
        BigDecimal price,
        BigDecimal quantity,
        Instant placedAt
) implements OrderEvent {}
