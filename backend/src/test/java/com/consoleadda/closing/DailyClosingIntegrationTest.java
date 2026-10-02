package com.consoleadda.closing;

import com.consoleadda.support.Phase4TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Each test works on its own far-future business day (see {@link #moveToFreshBusinessDay()}), so closing a day
 * never affects other tests that share the database.
 */
class DailyClosingIntegrationTest extends Phase4TestSupport {

    private ResultActions close(String token, LocalDate date, String opening, String actual) throws Exception {
        return mockMvc.perform(post("/api/daily-closing/{date}/close", date).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("openingCash", opening, "actualCash", actual, "notes", "Counted by test"))));
    }

    private JsonNode day(String token, LocalDate date, String openingCash) throws Exception {
        var request = get("/api/daily-closing/{date}", date).header("Authorization", bearer(token));
        if (openingCash != null) {
            request.param("openingCash", openingCash);
        }
        return readJson(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void dayTotalsCashReconciliationCloseAndReopen() throws Exception {
        String op = operatorToken();
        String admin = adminToken();
        long station = createStation();
        long food = createFoodItem("Closing Fries", "50.00", 0, false);
        long game = createGame("Closing Game", "2000.00", "199.00", 7, "20.00", "500.00");
        LocalDate day = moveToFreshBusinessDay();

        // 1 h gaming paid in cash; 2 × food by UPI; a rental (charge + ₹500 deposit) in cash;
        // a card sale that the admin cancels (refund in card).
        long session = start(op, station, 1).get("id").asLong();
        clock.advance(Duration.ofMinutes(60));
        BigDecimal gaming = complete(op, session, "CASH").get("charges").get("totalAmount").decimalValue();
        BigDecimal upi = walkIn(op, food, 2, "UPI", null).get("totalAmount").decimalValue();
        BigDecimal rental = rent(op, game, uniqueMobile(), null, null, "CASH").get("totalAmount").decimalValue();
        JsonNode card = walkIn(op, food, 1, "CARD", null);
        BigDecimal cardTotal = card.get("totalAmount").decimalValue();
        mockMvc.perform(post("/api/bills/{id}/cancel", card.get("id").asLong()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Wrong item"))))
                .andExpect(status().isOk());

        JsonNode live = day(op, day, "1000");
        assertThat(live.get("live").asBoolean()).isTrue();
        assertThat(live.get("status").asText()).isEqualTo("OPEN");
        JsonNode f = live.get("figures");
        assertThat(f.get("billCount").asInt()).isEqualTo(3); // the cancelled bill is not a sale
        assertThat(f.get("sessionCount").asInt()).isEqualTo(1);
        assertThat(f.get("gamingMinutes").asInt()).isEqualTo(60);
        assertThat(f.get("foodUnitsSold").asInt()).isEqualTo(2);
        assertThat(f.get("totalRevenue").decimalValue()).isEqualByComparingTo(gaming.add(upi).add(rental));
        assertThat(f.get("cash").decimalValue()).isEqualByComparingTo(gaming.add(rental));
        assertThat(f.get("upi").decimalValue()).isEqualByComparingTo(upi);
        assertThat(f.get("card").decimalValue()).isEqualByComparingTo(cardTotal);
        assertThat(f.get("refundTotal").decimalValue()).isEqualByComparingTo(cardTotal);
        assertThat(f.get("depositTotal").decimalValue()).isEqualByComparingTo("500");
        BigDecimal expectedCash = new BigDecimal("1000").add(gaming).add(rental).add(new BigDecimal("500"));
        assertThat(live.get("cash").get("expectedCash").decimalValue()).isEqualByComparingTo(expectedCash);

        // Close the day once it is over (the next morning).
        clock.advance(Duration.ofDays(1));
        BigDecimal counted = expectedCash.subtract(BigDecimal.TEN);
        close(op, day, "1000", counted.toPlainString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.live").value(false))
                .andExpect(jsonPath("$.cashDifference").value(-10.0))
                .andExpect(jsonPath("$.closedBy").value("operator"));
        close(op, day, "1000", "0").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAY_ALREADY_CLOSED"));

        // The frozen snapshot is returned from now on.
        JsonNode frozen = day(op, day, null);
        assertThat(frozen.get("live").asBoolean()).isFalse();
        assertThat(frozen.get("figures").get("totalRevenue").decimalValue()).isEqualByComparingTo(gaming.add(upi).add(rental));
        assertThat(frozen.get("cash").get("expectedCash").decimalValue()).isEqualByComparingTo(expectedCash);
        assertThat(frozen.get("actualCash").decimalValue()).isEqualByComparingTo(counted);

        // A closed day can't be changed: cancelling one of its bills is refused.
        long upiBill = jdbc.queryForObject("SELECT id FROM bills WHERE business_date = ? AND status = 'PAID' "
                + "AND bill_type = 'WALK_IN'", Long.class, day);
        mockMvc.perform(post("/api/bills/{id}/cancel", upiBill).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Late correction"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DAY_CLOSED"));

        // Only an admin can reopen, with a reason.
        mockMvc.perform(post("/api/daily-closing/{date}/reopen", day).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Fix"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/daily-closing/{date}/reopen", day).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", ""))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/daily-closing/{date}/reopen", day).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Late correction"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REOPENED"))
                .andExpect(jsonPath("$.live").value(true))
                .andExpect(jsonPath("$.reopenedBy").value("admin"));
        mockMvc.perform(post("/api/bills/{id}/cancel", upiBill).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Late correction"))))
                .andExpect(status().isOk());
        assertThat(day(op, day, null).get("figures").get("totalRevenue").decimalValue())
                .isEqualByComparingTo(gaming.add(rental));
        close(admin, day, "1000", counted.toPlainString()).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'DAY_CLOSED' AND entity_id = ?",
                Integer.class, day.toString())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'DAY_REOPENED' AND entity_id = ?",
                Integer.class, day.toString())).isEqualTo(1);
        String history = mockMvc.perform(get("/api/daily-closing").param("from", day.toString()).param("to", day.toString())
                        .header("Authorization", bearer(op)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(readJson(history)).hasSize(1);
    }

    @Test
    void cannotCloseTheFutureOrADayWithRunningSessions() throws Exception {
        String op = operatorToken();
        long station = createStation();
        LocalDate day = moveToFreshBusinessDay();
        close(op, day.plusDays(1), "0", "0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FUTURE_DATE"));

        long session = start(op, station, 1).get("id").asLong();
        close(op, day, "0", "0").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSIONS_STILL_ACTIVE"));
        mockMvc.perform(post("/api/sessions/{id}/cancel", session).header("Authorization", bearer(op))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "test cleanup"))))
                .andExpect(status().isOk());
        close(op, day, "0", "-1").andExpect(status().isBadRequest());
    }

    @Test
    void closedDayRefusesNewSessionsSalesRentalsAndStockEntries() throws Exception {
        String op = operatorToken();
        String admin = adminToken();
        long station = createStation();
        long food = createFoodItem("Closed Chips", "20.00", 0, true);
        long game = createGame("Closed Game", "1000.00", "100.00", 7, "10.00", "0.00");
        LocalDate day = moveToFreshBusinessDay();
        // Simulate the day already being closed (closing "today" itself is blocked while other tests' sessions run).
        jdbc.update("INSERT INTO daily_closings (business_date, status) VALUES (?, 'CLOSED')", day);

        startRequest(op, station, 1, null, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAY_CLOSED"));
        walkInRequest(op, food, 1, "CASH", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAY_CLOSED"));
        rentRequest(op, game, uniqueMobile(), null, null, "CASH").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAY_CLOSED"));
        stockEntry(admin, food, "PURCHASE", 5).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DAY_CLOSED"));
    }

    /**
     * Sales racing with the closing: each sale either lands before the closing (and is in its totals) or is
     * refused with DAY_CLOSED. None can slip in unaccounted.
     */
    @Test
    void salesRacingTheClosingAreEitherCountedOrRefused() throws Exception {
        String op = operatorToken();
        long food = createFoodItem("Race Soda", "30.00", 0, false);
        LocalDate day = moveToFreshBusinessDay();
        // Sessions left running by other test classes would block closing "today"; they belong to past days.
        jdbc.update("UPDATE gaming_sessions SET status = 'CANCELLED', cancel_reason = 'test cleanup' WHERE status = 'ACTIVE'");
        walkIn(op, food, 1, "CASH", null);

        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> walkInRequest(op, food, 1, "CASH", null).andReturn());
        }
        tasks.add(() -> close(op, day, "0", "0").andReturn());
        List<MvcResult> results = race(tasks);

        MvcResult closing = results.getLast();
        assertThat(closing.getResponse().getStatus()).isEqualTo(200);
        List<MvcResult> sales = results.subList(0, 8);
        sales.stream().filter(r -> r.getResponse().getStatus() != 201).forEach(r -> {
            assertThat(r.getResponse().getStatus()).isEqualTo(409);
            try {
                assertThat(r.getResponse().getContentAsString()).contains("DAY_CLOSED");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        int paid = jdbc.queryForObject("SELECT COUNT(*) FROM bills WHERE business_date = ? AND status = 'PAID'",
                Integer.class, day);
        assertThat(paid).isEqualTo(1 + count(sales, 201));
        assertThat(readJson(closing.getResponse().getContentAsString()).get("figures").get("billCount").asInt())
                .isEqualTo(paid);
    }
}
