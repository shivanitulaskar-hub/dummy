package com.consoleadda.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Helpers for tests that drive gaming sessions through the real API. Each test uses its own station. */
public abstract class SessionTestSupport extends IntegrationTest {

    protected long createStation() throws Exception {
        String code = "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String body = mockMvc.perform(post("/api/stations").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "name", "Test " + code, "displayOrder", 999))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readJson(body).get("id").asLong();
    }

    protected ResultActions startRequest(String token, long stationId, int players, String mobile, String name)
            throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("stationId", stationId);
        request.put("playerCount", players);
        if (mobile != null) {
            request.put("customerMobile", mobile);
        }
        if (name != null) {
            request.put("customerName", name);
        }
        return mockMvc.perform(post("/api/sessions").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(request)));
    }

    protected JsonNode start(String token, long stationId, int players) throws Exception {
        String body = startRequest(token, stationId, players, null, null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return readJson(body);
    }

    protected ResultActions completeRequest(String token, long sessionId, String paymentMode, String idempotencyKey)
            throws Exception {
        var builder = post("/api/sessions/{id}/complete", sessionId).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("paymentMode", paymentMode)));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(builder);
    }

    protected JsonNode complete(String token, long sessionId, String paymentMode) throws Exception {
        String body = completeRequest(token, sessionId, paymentMode, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return readJson(body);
    }
}
