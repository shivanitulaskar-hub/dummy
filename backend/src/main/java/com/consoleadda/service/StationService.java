package com.consoleadda.service;

import com.consoleadda.dto.station.StationResponse;
import com.consoleadda.mapper.StationMapper;
import com.consoleadda.repository.GamingStationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class StationService {

    private final GamingStationRepository stationRepository;
    private final StationMapper stationMapper;

    public StationService(GamingStationRepository stationRepository, StationMapper stationMapper) {
        this.stationRepository = stationRepository;
        this.stationMapper = stationMapper;
    }

    public List<StationResponse> list(boolean includeInactive) {
        var stations = includeInactive
                ? stationRepository.findAllByOrderByDisplayOrderAscCodeAsc()
                : stationRepository.findByActiveTrueOrderByDisplayOrderAscCodeAsc();
        return stations.stream().map(stationMapper::toResponse).toList();
    }
}
