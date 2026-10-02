package com.consoleadda.controller;

import com.consoleadda.dto.station.StationResponse;
import com.consoleadda.service.StationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    public List<StationResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return stationService.list(includeInactive);
    }
}
