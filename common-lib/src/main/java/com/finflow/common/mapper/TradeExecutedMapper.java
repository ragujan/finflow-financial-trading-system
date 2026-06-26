package com.finflow.common.mapper;

import com.finflow.common.dto.TradeExecutedDto;
import com.finflow.common.events.TradeExecuted;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta-cdi")
public interface TradeExecutedMapper {

    TradeExecutedDto toDto(TradeExecuted event);

    TradeExecuted toEvent(TradeExecutedDto dto);
}
