package com.finflow.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderAmended(
        UUID orderId,
        BigDecimal price,
        BigDecimal quantity,
        Instant amendedAt
) implements OrderEvent {}
