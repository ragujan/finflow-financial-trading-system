package com.finflow.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSettledDto {

    private UUID paymentId;
    private UUID tradeId;
    private UUID buyerId;
    private UUID sellerId;
    private String symbol;
    private BigDecimal amount;
    private Instant settledAt;
}
