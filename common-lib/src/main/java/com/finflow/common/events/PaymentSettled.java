package com.finflow.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSettled(
        UUID paymentId,
        UUID tradeId,
        UUID buyerId,
        UUID sellerId,
        String symbol,
        BigDecimal amount,
        Instant settledAt
) implements PaymentEvent {}
