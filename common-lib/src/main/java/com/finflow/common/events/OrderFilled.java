package com.finflow.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderFilled(
        UUID orderId,
        BigDecimal filledQuantity,
        Instant filledAt
) implements OrderEvent {}
