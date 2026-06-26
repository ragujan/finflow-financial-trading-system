package com.finflow.common.domain;

import java.util.Objects;
import java.util.UUID;

public record OrderId(UUID value) {

    public OrderId {
        Objects.requireNonNull(value, "orderId must not be null");
    }

    public static OrderId of(UUID value) {
        return new OrderId(value);
    }

    public static OrderId random() {
        return new OrderId(UUID.randomUUID());
    }
}
