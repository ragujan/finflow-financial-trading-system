package com.finflow.common.mapper;

import com.finflow.common.dto.OrderPlacedDto;
import com.finflow.common.events.OrderPlaced;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta-cdi")
public interface OrderPlacedMapper {

    OrderPlacedDto toDto(OrderPlaced event);

    OrderPlaced toEvent(OrderPlacedDto dto);
}
