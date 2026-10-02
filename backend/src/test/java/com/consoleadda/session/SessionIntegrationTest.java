package com.consoleadda.session;

import com.consoleadda.support.SessionTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Year;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionIntegrationTest extends SessionTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void startSessionMarksStationOccupiedAndShowsLiveEstimate() throws Exception {
        long station = createStation();
        String op = operatorToken();
        JsonNode session = start(op, station, 3);
        assertThat(session.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(session.get("pricingPlanName").asText()).isEqualTo("Standard Weekday");
        assertThat(session.get("billNumber").isNull()).isTrue();

        clock.advance(Duration.ofMinutes(84)); // 1h 24m → billable 1h 30m with 3 players (per-hour default)
        mockMvc.perform(get("/api/sessions/active").header("Authorization", bearer(op)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.stationId == " + station + ")].charges.actualMinutes").value(84))
                .andExpect(jsonPath("$[?(@.stationId == " + station + ")].charges.billableMinutes").value(90))
                .andExpect(jsonPath("$[?(@.stationId == " + station + ")].charges.totalAmount").value(285.0));
    }

    @Test
    void duplicateSessionOnSameStationIsRejected() throws Exception {
        long station = createStation();
        String op = operatorToken();
        start(op, station, 1);
        startRequest(op, station, 2, null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATION_OCCUPIED"));
    }

    @Test
    void invalidStartRequestsAreRejected() throws Exception {
        long station = createStation();
        String op = operatorToken();
        startRequest(op, station, 5, null, null)  // plan allows max 4 players
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PLAYER_COUNT"));
        startRequest(op, station, 0, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        startRequest(op, station, 1, "12345", "Rahul")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MOBILE"));
        startRequest(op, 999_999, 1, null, null)
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivatedStationCannotStartSession() throws Exception {
        long station = createStation();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/stations/{id}", station)
                        .header("Authorization", bearer(adminToken())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "OFF-" + station, "name", "Off", "active", false))))
                .andExpect(status().isOk());
        startRequest(operatorToken(), station, 1, null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATION_INACTIVE"));
    }

    @Test
    void endPreviewThenCompleteCreatesNumberedPaidBillAndFreesStation() throws Exception {
        long station = createStation();
        String op = operatorToken();
        String mobile = "98" + String.format("%08d", System.nanoTime() % 100_000_000L);
        String body = startRequest(op, station, 1, "+91 " + mobile, "Rahul")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value("Rahul"))
                .andExpect(jsonPath("$.customerMobile").value(mobile))
                .andReturn().getResponse().getContentAsString();
        long id = readJson(body).get("id").asLong();

        clock.advance(Duration.ofMinutes(71)); // 1h 11m → next interval: 1h 30m = ₹135
        mockMvc.perform(post("/api/sessions/{id}/end", id).header("Authorization", bearer(op)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.charges.billableMinutes").value(90))
                .andExpect(jsonPath("$.charges.gamingAmount").value(135.0))
                .andExpect(jsonPath("$.charges.gamingExplanation").value("1h slab ₹90 + 1 × 30m extension ₹45"));

        JsonNode done = complete(op, id, "UPI");
        assertThat(done.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.get("paymentMode").asText()).isEqualTo("UPI");
        assertThat(done.get("charges").get("totalAmount").decimalValue()).isEqualByComparingTo("135");
        String billNumber = done.get("billNumber").asText();
        assertThat(billNumber).matches("CA-" + Year.now().getValue() + "-\\d{6}");

        // Station is free again.
        mockMvc.perform(get("/api/sessions/active").header("Authorization", bearer(op)))
                .andExpect(jsonPath("$[*].stationId", not(hasItem((int) station))));
        start(op, station, 1);

        // Bill, line, payment and customer totals were saved.
        long billId = done.get("billId").asLong();
        assertThat(jdbc.queryForObject("SELECT status FROM bills WHERE id = ?", String.class, billId)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT total_amount FROM bills WHERE id = ?", BigDecimal.class, billId))
                .isEqualByComparingTo("135.00");
        assertThat(jdbc.queryForObject("SELECT description FROM bill_items WHERE bill_id = ?", String.class, billId))
                .contains("1h 30m · 1 player");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE bill_id = ? AND mode = 'UPI' AND amount = 135.00",
                Integer.class, billId)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT total_sessions, total_gaming_minutes, total_spent FROM customers WHERE mobile = ?",
                mobile)).containsEntry("total_sessions", 1).containsEntry("total_gaming_minutes", 90)
                .containsEntry("total_spent", new BigDecimal("135.00"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'BILL_CREATED' AND entity_id = ?",
                Integer.class, String.valueOf(billId))).isEqualTo(1);
    }

    @Test
    void billNumbersAreSequential() throws Exception {
        String op = operatorToken();
        long a = start(op, createStation(), 1).get("id").asLong();
        long b = start(op, createStation(), 1).get("id").asLong();
        String first = complete(op, a, "CASH").get("billNumber").asText();
        String second = complete(op, b, "CASH").get("billNumber").asText();
        long n1 = Long.parseLong(first.substring(first.lastIndexOf('-') + 1));
        long n2 = Long.parseLong(second.substring(second.lastIndexOf('-') + 1));
        assertThat(n2).isEqualTo(n1 + 1);
    }

    @Test
    void completingTwiceIsRejectedButSameIdempotencyKeyReturnsSameBill() throws Exception {
        String op = operatorToken();
        long id = start(op, createStation(), 2).get("id").asLong();
        clock.advance(Duration.ofMinutes(60));

        String first = completeRequest(op, id, "CASH", "key-" + id).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String retry = completeRequest(op, id, "CASH", "key-" + id).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(readJson(retry).get("billNumber").asText()).isEqualTo(readJson(first).get("billNumber").asText());
        assertThat(readJson(first).get("charges").get("totalAmount").decimalValue()).isEqualByComparingTo("140");

        completeRequest(op, id, "CASH", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_ACTIVE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments p JOIN gaming_sessions s ON s.bill_id = p.bill_id "
                + "WHERE s.id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void endTimeFromPreviewIsHonouredWithinLimits() throws Exception {
        String op = operatorToken();
        long id = start(op, createStation(), 1).get("id").asLong();
        clock.advance(Duration.ofMinutes(65));
        String preview = mockMvc.perform(post("/api/sessions/{id}/end", id).header("Authorization", bearer(op)))
                .andReturn().getResponse().getContentAsString();
        String endedAt = readJson(preview).get("charges").get("calculatedAt").asText();

        clock.advance(Duration.ofMinutes(8)); // operator took a while; customer still pays for 1h 5m → 1h
        String body = mockMvc.perform(post("/api/sessions/{id}/complete", id).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentMode", "CARD", "endedAt", endedAt))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(readJson(body).get("charges").get("billableMinutes").asInt()).isEqualTo(60);
        assertThat(readJson(body).get("charges").get("totalAmount").decimalValue()).isEqualByComparingTo("90");

        long other = start(op, createStation(), 1).get("id").asLong();
        String future = clock.instant().plus(Duration.ofMinutes(10)).toString();
        mockMvc.perform(post("/api/sessions/{id}/complete", other).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentMode", "CASH", "endedAt", future))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_END_TIME"));
    }

    @Test
    void fifteenMinuteCreditReducesBillableTime() throws Exception {
        String op = operatorToken();
        long id = start(op, createStation(), 1).get("id").asLong();
        // Phase 3 grants credits from qualifying drinks; the engine path is exercised directly here.
        jdbc.update("UPDATE gaming_sessions SET credit_minutes = 15 WHERE id = ?", id);
        clock.advance(Duration.ofMinutes(75)); // 1h 15m − 15m credit = 1h
        JsonNode done = complete(op, id, "CASH");
        assertThat(done.get("charges").get("creditMinutes").asInt()).isEqualTo(15);
        assertThat(done.get("charges").get("billableMinutes").asInt()).isEqualTo(60);
        assertThat(done.get("charges").get("totalAmount").decimalValue()).isEqualByComparingTo("90");
    }

    @Test
    void playerCountCanBeChangedDuringSession() throws Exception {
        String op = operatorToken();
        long id = start(op, createStation(), 1).get("id").asLong();
        mockMvc.perform(patch("/api/sessions/{id}", id).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("playerCount", 4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerCount").value(4))
                .andExpect(jsonPath("$.charges.totalAmount").value(240.0));
        mockMvc.perform(patch("/api/sessions/{id}", id).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("playerCount", 9))))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'SESSION_UPDATED' AND entity_id = ?",
                Integer.class, String.valueOf(id))).isEqualTo(1);
    }

    @Test
    void operatorCanCancelOnlyRecentSessionsAdminAnyTime() throws Exception {
        String op = operatorToken();
        long recent = start(op, createStation(), 1).get("id").asLong();
        mockMvc.perform(post("/api/sessions/{id}/cancel", recent).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Started on wrong PS5"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Started on wrong PS5"));

        long old = start(op, createStation(), 1).get("id").asLong();
        clock.advance(Duration.ofMinutes(40));
        mockMvc.perform(post("/api/sessions/{id}/cancel", old).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "test"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CANCEL_WINDOW_PASSED"));
        mockMvc.perform(post("/api/sessions/{id}/cancel", old).header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Power cut"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/sessions/{id}/cancel", old).header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", ""))))
                .andExpect(status().isBadRequest());
        // Cancelled sessions cannot be completed and create no payment.
        completeRequest(op, old, "CASH", null).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT b.status FROM bills b JOIN gaming_sessions s ON s.bill_id = b.id WHERE s.id = ?",
                String.class, old)).isEqualTo("CANCELLED");
    }

    @Test
    void closedBusinessDayBlocksCompletion() throws Exception {
        String op = operatorToken();
        long id = start(op, createStation(), 1).get("id").asLong();
        clock.advance(Duration.ofDays(400)); // far-future business day that we close
        String date = clock.instant().atZone(java.time.ZoneId.of("Asia/Kolkata")).minusHours(6).toLocalDate().toString();
        jdbc.update("INSERT INTO daily_closings (business_date, status) VALUES (?, 'CLOSED')", date);
        try {
            mockMvc.perform(post("/api/sessions/{id}/complete", id).header("Authorization", bearer(op))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("paymentMode", "CASH"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DAY_CLOSED"));
        } finally {
            jdbc.update("DELETE FROM daily_closings WHERE business_date = ?", date);
        }
    }

    @Test
    void sessionsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/sessions/active")).andExpect(status().isUnauthorized());
    }
}
