package com.consoleadda.mapper;

import com.consoleadda.dto.station.StationResponse;
import com.consoleadda.entity.GamingStation;
import org.springframework.stereotype.Component;

@Component
public class StationMapper {

    public StationResponse toResponse(GamingStation station) {
        return new StationResponse(station.getId(), station.getCode(), station.getName(), station.getConsoleLabel(),
                station.getTvLabel(), station.isActive(), station.getDisplayOrder(), station.getNotes());
    }
}
