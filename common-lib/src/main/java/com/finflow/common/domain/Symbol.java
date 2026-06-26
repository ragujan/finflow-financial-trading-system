package com.finflow.common.domain;

public record Symbol(String value) {

    public Symbol {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        value = value.toUpperCase();
    }
}
