package com.finflow.common.events;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelled(
        UUID orderId,
        Instant cancelledAt
) implements OrderEvent {}
