package com.finflow.common.mapper;

import com.finflow.common.dto.PaymentSettledDto;
import com.finflow.common.events.PaymentSettled;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta-cdi")
public interface PaymentSettledMapper {

    PaymentSettledDto toDto(PaymentSettled event);

    PaymentSettled toEvent(PaymentSettledDto dto);
}
