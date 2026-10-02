package com.consoleadda.dto.station;

public record StationResponse(
        Long id,
        String code,
        String name,
        String consoleLabel,
        String tvLabel,
        boolean active,
        int displayOrder,
        String notes
) {
}
