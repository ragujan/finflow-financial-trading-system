package com.finflow.common.events;

import com.finflow.common.domain.OrderSide;
import com.finflow.common.domain.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public sealed interface OrderEvent permits
        OrderPlaced,
        OrderCancelled,
        OrderAmended,
        OrderFilled,
        OrderPartiallyFilled,
        TradeExecuted {}
