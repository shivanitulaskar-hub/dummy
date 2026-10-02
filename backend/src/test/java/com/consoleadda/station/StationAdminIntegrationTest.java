package com.consoleadda.station;

import com.consoleadda.support.SessionTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StationAdminIntegrationTest extends SessionTestSupport {

    @Test
    void adminCanAddEditAndDeleteUnusedStation() throws Exception {
        String admin = adminToken();
        String body = mockMvc.perform(post("/api/stations").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "ps5-x1", "name", "PS5 Station X", "tvLabel", "TV #X (55\")"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PS5-X1"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = readJson(body).get("id").asLong();

        mockMvc.perform(put("/api/stations/{id}", id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "PS5-X1", "name", "Renamed", "notes", "Near window"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));

        mockMvc.perform(delete("/api/stations/{id}", id).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
    }

    @Test
    void duplicateCodeIsRejected() throws Exception {
        mockMvc.perform(post("/api/stations").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("code", "PS5-01", "name", "Dup"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
    }

    @Test
    void invalidStationIsRejected() throws Exception {
        mockMvc.perform(post("/api/stations").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("code", "bad code!", "name", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.length()").value(2));
    }

    @Test
    void operatorCannotManageStations() throws Exception {
        String op = operatorToken();
        mockMvc.perform(post("/api/stations").header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("code", "PS5-99", "name", "X"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/stations/1").header("Authorization", bearer(op)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/stations").header("Authorization", bearer(op)))
                .andExpect(status().isOk());
    }

    @Test
    void stationWithActiveSessionCannotBeDeactivatedAndUsedStationCannotBeDeleted() throws Exception {
        long station = createStation();
        long session = start(operatorToken(), station, 1).get("id").asLong();
        String admin = adminToken();
        mockMvc.perform(put("/api/stations/{id}", station).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "USED-" + station, "name", "Used", "active", false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATION_OCCUPIED"));
        complete(admin, session, "CASH");
        mockMvc.perform(delete("/api/stations/{id}", station).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATION_HAS_HISTORY"));
        mockMvc.perform(put("/api/stations/{id}", station).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "USED-" + station, "name", "Used", "active", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
