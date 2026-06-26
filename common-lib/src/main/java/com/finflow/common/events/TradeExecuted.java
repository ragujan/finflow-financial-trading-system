package com.finflow.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeExecuted(
        UUID tradeId,
        UUID buyOrderId,
        UUID sellOrderId,
        String symbol,
        BigDecimal price,
        BigDecimal quantity,
        Instant executedAt
) implements OrderEvent {}
