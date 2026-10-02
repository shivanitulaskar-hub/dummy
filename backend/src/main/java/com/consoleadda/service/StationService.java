package com.consoleadda.service;

import com.consoleadda.dto.station.StationRequest;
import com.consoleadda.dto.station.StationResponse;
import com.consoleadda.entity.GamingStation;
import com.consoleadda.entity.SessionStatus;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.exception.NotFoundException;
import com.consoleadda.mapper.StationMapper;
import com.consoleadda.repository.GamingSessionRepository;
import com.consoleadda.repository.GamingStationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Gaming Station Master. The number of stations is data: admins add, edit, deactivate or remove them. */
@Service
public class StationService {

    private final GamingStationRepository stationRepository;
    private final GamingSessionRepository sessionRepository;
    private final StationMapper stationMapper;
    private final AuditService auditService;

    public StationService(GamingStationRepository stationRepository, GamingSessionRepository sessionRepository,
                          StationMapper stationMapper, AuditService auditService) {
        this.stationRepository = stationRepository;
        this.sessionRepository = sessionRepository;
        this.stationMapper = stationMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<StationResponse> list(boolean includeInactive) {
        var stations = includeInactive
                ? stationRepository.findAllByOrderByDisplayOrderAscCodeAsc()
                : stationRepository.findByActiveTrueOrderByDisplayOrderAscCodeAsc();
        return stations.stream().map(stationMapper::toResponse).toList();
    }

    @Transactional
    public StationResponse create(StationRequest request) {
        String code = normalizeCode(request.code());
        if (stationRepository.existsByCodeIgnoreCase(code)) {
            throw BusinessException.conflict("DUPLICATE_CODE", "Station " + code + " already exists");
        }
        GamingStation station = new GamingStation();
        apply(station, request, code);
        stationRepository.saveAndFlush(station);
        StationResponse response = stationMapper.toResponse(station);
        auditService.record("STATION_CREATED", "GamingStation", station.getId(), null, response);
        return response;
    }

    @Transactional
    public StationResponse update(Long id, StationRequest request) {
        GamingStation station = stationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Station", id));
        String code = normalizeCode(request.code());
        if (stationRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw BusinessException.conflict("DUPLICATE_CODE", "Station " + code + " already exists");
        }
        boolean deactivating = station.isActive() && Boolean.FALSE.equals(request.active());
        if (deactivating && sessionRepository.existsByStationIdAndStatus(id, SessionStatus.ACTIVE)) {
            throw BusinessException.conflict("STATION_OCCUPIED",
                    station.getCode() + " has an active session. End it before deactivating the station.");
        }
        StationResponse before = stationMapper.toResponse(station);
        apply(station, request, code);
        stationRepository.saveAndFlush(station);
        StationResponse after = stationMapper.toResponse(station);
        auditService.record("STATION_UPDATED", "GamingStation", id, before, after);
        return after;
    }

    /** Only stations that were never used can be deleted; others keep their history and are deactivated. */
    @Transactional
    public void delete(Long id) {
        GamingStation station = stationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Station", id));
        if (sessionRepository.existsByStationId(id)) {
            throw BusinessException.conflict("STATION_HAS_HISTORY",
                    station.getCode() + " has session history. Deactivate it instead of deleting it.");
        }
        StationResponse before = stationMapper.toResponse(station);
        stationRepository.delete(station);
        auditService.record("STATION_DELETED", "GamingStation", id, before, null);
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private static void apply(GamingStation station, StationRequest request, String code) {
        station.setCode(code);
        station.setName(request.name().trim());
        station.setConsoleLabel(request.consoleLabel());
        station.setTvLabel(request.tvLabel());
        if (request.active() != null) {
            station.setActive(request.active());
        }
        if (request.displayOrder() != null) {
            station.setDisplayOrder(request.displayOrder());
        }
        station.setNotes(request.notes());
    }
}
