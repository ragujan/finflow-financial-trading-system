package com.finflow.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderPartiallyFilled(
        UUID orderId,
        BigDecimal filledQuantity,
        BigDecimal remainingQuantity,
        Instant filledAt
) implements OrderEvent {}
