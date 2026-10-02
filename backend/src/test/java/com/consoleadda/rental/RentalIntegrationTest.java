package com.consoleadda.rental;

import com.consoleadda.support.Phase4TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RentalIntegrationTest extends Phase4TestSupport {

    private List<String> payments(long billId) {
        return jdbc.queryForList("SELECT CONCAT(payment_type, ':', mode, ':', amount) FROM payments WHERE bill_id = ? "
                + "ORDER BY id", String.class, billId);
    }

    @Test
    void rentAndReturnOnTimeRefundsDeposit() throws Exception {
        String op = operatorToken();
        long game = createGame("God of War", "2599.00", "199.00", 7, "30.00", "500.00");
        String mobile = uniqueMobile();

        JsonNode rental = rent(op, game, mobile, null, null, "CASH");
        long id = rental.get("id").asLong();
        assertThat(rental.get("rentalNumber").asText()).matches("RN-\\d{4}-\\d{6}");
        assertThat(rental.get("status").asText()).isEqualTo("OUT");
        assertThat(rental.get("rentalDays").asInt()).isEqualTo(7);
        assertThat(rental.get("rentalPrice").decimalValue()).isEqualByComparingTo("199");
        assertThat(rental.get("depositAmount").decimalValue()).isEqualByComparingTo("500");
        assertThat(Duration.between(Instant.parse(rental.get("outAt").asText()),
                Instant.parse(rental.get("expectedReturnAt").asText()))).isEqualTo(Duration.ofDays(7));
        long billId = rental.get("billId").asLong();
        assertThat(rental.get("billNumber").asText()).isNotBlank();
        assertThat(payments(billId)).containsExactly("PAYMENT:CASH:199.00", "DEPOSIT:CASH:500.00");
        assertThat(jdbc.queryForObject("SELECT bill_type FROM bills WHERE id = ?", String.class, billId)).isEqualTo("RENTAL");
        assertThat(jdbc.queryForObject("SELECT total_spent FROM customers WHERE mobile = ?", BigDecimal.class, mobile))
                .isEqualByComparingTo("199");

        // The disc shows as rented, and cannot go out twice.
        mockMvc.perform(get("/api/games/{id}", game).header("Authorization", bearer(op)))
                .andExpect(jsonPath("$.currentRental.rentalId").value(id))
                .andExpect(jsonPath("$.currentRental.overdue").value(false));
        rentRequest(op, game, uniqueMobile(), null, null, "UPI")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GAME_ALREADY_RENTED"));

        clock.advance(Duration.ofDays(6));
        mockMvc.perform(get("/api/rentals/{id}/return-preview", id).header("Authorization", bearer(op)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lateDays").value(0))
                .andExpect(jsonPath("$.lateFee").value(0.0))
                .andExpect(jsonPath("$.depositToRefund").value(500.0))
                .andExpect(jsonPath("$.depositMode").value("CASH"));
        returnRequest(op, id, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.lateDays").value(0))
                .andExpect(jsonPath("$.depositRefunded").value(500.0))
                .andExpect(jsonPath("$.lateFeeBillId").isEmpty())
                .andExpect(jsonPath("$.totalAmount").value(199.0));
        assertThat(payments(billId)).containsExactly("PAYMENT:CASH:199.00", "DEPOSIT:CASH:500.00",
                "DEPOSIT_REFUND:CASH:500.00");
        returnRequest(op, id, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RENTAL_NOT_OUT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'RENTAL_RETURNED' AND entity_id = ?",
                Integer.class, String.valueOf(id))).isEqualTo(1);

        // Available again.
        rent(op, game, uniqueMobile(), 3, "0", "UPI");
    }

    @Test
    void overdueRentalChargesLateFeeOnItsOwnBill() throws Exception {
        String op = operatorToken();
        long game = createGame("Hitman", "1999.00", "199.00", 7, "30.00", "0.00");
        JsonNode rental = rent(op, game, uniqueMobile(), 3, null, "CASH");
        long id = rental.get("id").asLong();
        assertThat(rental.get("rentalPrice").decimalValue()).isEqualByComparingTo("199"); // 3 days = one 7-day period
        assertThat(payments(rental.get("billId").asLong())).containsExactly("PAYMENT:CASH:199.00");

        // Due after 3 days; 2 days 1 hour late (past the 2-hour grace) = 3 started days.
        clock.advance(Duration.ofDays(5).plusHours(1));
        mockMvc.perform(get("/api/rentals/{id}", id).header("Authorization", bearer(op)))
                .andExpect(jsonPath("$.status").value("OVERDUE"))
                .andExpect(jsonPath("$.accruedLateDays").value(3))
                .andExpect(jsonPath("$.accruedLateFee").value(90.0));
        String overdue = mockMvc.perform(get("/api/rentals").param("status", "OVERDUE").param("size", "200")
                        .header("Authorization", bearer(op)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(readJson(overdue).get("content").findValuesAsText("rentalNumber"))
                .contains(rental.get("rentalNumber").asText());

        returnRequest(op, id, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_MODE_REQUIRED"));
        JsonNode returned = readJson(returnRequest(op, id, "UPI").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(returned.get("status").asText()).isEqualTo("RETURNED");
        assertThat(returned.get("lateDays").asInt()).isEqualTo(3);
        assertThat(returned.get("lateFee").decimalValue()).isEqualByComparingTo("90");
        long lateBill = returned.get("lateFeeBillId").asLong();
        assertThat(returned.get("lateFeeBillNumber").asText()).isNotBlank();
        assertThat(lateBill).isNotEqualTo(returned.get("billId").asLong());

        mockMvc.perform(get("/api/bills/{id}", lateBill).header("Authorization", bearer(op)))
                .andExpect(jsonPath("$.billType").value("RENTAL"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.lines[0].type").value("LATE_FEE"))
                .andExpect(jsonPath("$.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(30.0))
                .andExpect(jsonPath("$.rentalAmount").value(90.0))
                .andExpect(jsonPath("$.payments[0].mode").value("UPI"));
        BigDecimal lateTotal = jdbc.queryForObject("SELECT total_amount FROM bills WHERE id = ?", BigDecimal.class, lateBill);
        assertThat(returned.get("totalAmount").decimalValue())
                .isEqualByComparingTo(lateTotal.add(rental.get("totalAmount").decimalValue()));
    }

    @Test
    void returnOnTheDueDayWithinGraceIsNotLate() throws Exception {
        String op = operatorToken();
        long game = createGame("WuKong", "2599.00", "199.00", 7, "30.00", "0.00");
        long id = rent(op, game, uniqueMobile(), 2, null, "CASH").get("id").asLong();
        clock.advance(Duration.ofDays(2).plusHours(1)); // one hour late, inside the 2-hour grace
        returnRequest(op, id, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.lateDays").value(0))
                .andExpect(jsonPath("$.lateFee").value(0.0));
    }

    @Test
    void roiPerDisc() throws Exception {
        String op = operatorToken();
        String admin = adminToken();
        long game = createGame("Ghost of Yotei", "2599.00", "199.00", 7, "30.00", "0.00");
        mockMvc.perform(get("/api/games/{id}", game).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.roi.revenue").value(0.0))
                .andExpect(jsonPath("$.roi.breakEvenRentals").value(14)) // 2599 / 199 = 13.06
                .andExpect(jsonPath("$.roi.remaining").value(2599.0));

        long first = rent(op, game, uniqueMobile(), 7, null, "CASH").get("id").asLong();
        clock.advance(Duration.ofDays(7));
        returnRequest(op, first, null).andExpect(status().isOk());
        long second = rent(op, game, uniqueMobile(), 7, null, "CASH").get("id").asLong();
        clock.advance(Duration.ofDays(8)); // one day late
        returnRequest(op, second, "CASH").andExpect(status().isOk()).andExpect(jsonPath("$.lateFee").value(30.0));
        // A cancelled rental does not count.
        long third = rent(op, game, uniqueMobile(), 7, null, "CASH").get("id").asLong();
        mockMvc.perform(post("/api/rentals/{id}/cancel", third).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Wrong disc"))))
                .andExpect(status().isOk());

        // Revenue 199 + 199 + 30 = 428; remaining 2171 → 11 more rentals; ROI (428 − 2599) / 2599 = −83.53 %.
        mockMvc.perform(get("/api/games/{id}", game).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.roi.rentals").value(2))
                .andExpect(jsonPath("$.roi.revenue").value(428.0))
                .andExpect(jsonPath("$.roi.remaining").value(2171.0))
                .andExpect(jsonPath("$.roi.rentalsToRecover").value(11))
                .andExpect(jsonPath("$.roi.roiPercent").value(-83.53))
                .andExpect(jsonPath("$.roi.recovered").value(false));
        // Operators see neither purchase cost nor ROI.
        mockMvc.perform(get("/api/games/{id}", game).header("Authorization", bearer(op)))
                .andExpect(jsonPath("$.purchasePrice").isEmpty())
                .andExpect(jsonPath("$.roi").isEmpty());
    }

    @Test
    void cancelRefundsChargeAndDepositInTheSameMode() throws Exception {
        String op = operatorToken();
        String admin = adminToken();
        long game = createGame("Assassin's Creed", "2599.00", "199.00", 7, "30.00", "300.00");
        JsonNode rental = rent(op, game, uniqueMobile(), null, null, "UPI");
        long id = rental.get("id").asLong();
        long billId = rental.get("billId").asLong();

        // Rental bills can't be cancelled from the Bills screen.
        mockMvc.perform(post("/api/bills/{id}/cancel", billId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "test"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RENTAL_BILL"));

        mockMvc.perform(post("/api/rentals/{id}/cancel", id).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Entered by mistake"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.depositRefunded").value(300.0));
        assertThat(payments(billId)).containsExactly("PAYMENT:UPI:199.00", "DEPOSIT:UPI:300.00",
                "REFUND:UPI:199.00", "DEPOSIT_REFUND:UPI:300.00");
        assertThat(jdbc.queryForObject("SELECT status FROM bills WHERE id = ?", String.class, billId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT total_spent FROM customers WHERE mobile = ?", BigDecimal.class,
                rental.get("customerMobile").asText())).isEqualByComparingTo("0");

        // Operators only within the cancel window; admins any time.
        long late = rent(op, game, uniqueMobile(), null, "0", "CASH").get("id").asLong();
        clock.advance(Duration.ofMinutes(20));
        mockMvc.perform(post("/api/rentals/{id}/cancel", late).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Changed mind"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CANCEL_WINDOW_PASSED"));
        mockMvc.perform(post("/api/rentals/{id}/cancel", late).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Changed mind"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/rentals/{id}/cancel", late).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "again"))))
                .andExpect(status().isConflict());
    }

    @Test
    void simultaneousRentalsOfOneDiscLetExactlyOneThrough() throws Exception {
        String op = operatorToken();
        long game = createGame("Dragon Ball Z", "2599.00", "199.00", 7, "30.00", "0.00");
        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String mobile = uniqueMobile();
            tasks.add(() -> rentRequest(op, game, mobile, null, null, "CASH").andReturn());
        }
        List<MvcResult> results = race(tasks);
        assertThat(count(results, 201)).isEqualTo(1);
        assertThat(count(results, 409)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_rentals WHERE game_id = ?", Integer.class, game))
                .isEqualTo(1);
        // Losing requests rolled back completely: one rental bill, one payment.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments p JOIN game_rentals r ON r.bill_id = p.bill_id "
                + "WHERE r.game_id = ?", Integer.class, game)).isEqualTo(1);
    }

    @Test
    void retriedRentalWithSameKeyIsNotChargedTwice() throws Exception {
        String op = operatorToken();
        long game = createGame("Ghost of Tsushima", "2599.00", "199.00", 7, "0.00", "0.00");
        Map<String, Object> req = Map.of("gameId", game, "customerName", "Retry", "customerMobile", uniqueMobile(),
                "paymentMode", "CASH");
        String key = "rent-" + game;
        String first = mockMvc.perform(post("/api/rentals").header("Authorization", bearer(op)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String retry = mockMvc.perform(post("/api/rentals").header("Authorization", bearer(op)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(json(req)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat(readJson(retry).get("id").asLong()).isEqualTo(readJson(first).get("id").asLong());
    }

    @Test
    void validationAndPermissions() throws Exception {
        String op = operatorToken();
        String admin = adminToken();
        long game = createGame("Validation Game", "1000.00", "100.00", 7, "10.00", "0.00");
        rentRequest(op, game, "12345", null, null, "CASH")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MOBILE"));
        rentRequest(op, game, "", null, null, "CASH").andExpect(status().isBadRequest());
        rentRequest(op, game, uniqueMobile(), 0, null, "CASH").andExpect(status().isBadRequest());
        rentRequest(op, game, uniqueMobile(), null, "-5", "CASH").andExpect(status().isBadRequest());
        rentRequest(op, 999_999_999L, uniqueMobile(), null, null, "CASH").andExpect(status().isNotFound());

        Map<String, Object> update = new java.util.HashMap<>(Map.of("name", "Unavailable " + game, "copyNumber", 1,
                "purchasePrice", "1000.00", "rentalPrice", "100.00", "rentalPeriodDays", 7, "lateFeePerDay", "10.00",
                "defaultDeposit", "0.00", "availableForRental", false, "active", true));
        mockMvc.perform(put("/api/games/{id}", game).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/games/{id}", game).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isOk());
        rentRequest(op, game, uniqueMobile(), null, null, "CASH")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GAME_NOT_AVAILABLE"));

        // Same title + copy number twice is refused; copy 2 is fine.
        Map<String, Object> dup = new java.util.HashMap<>(update);
        mockMvc.perform(post("/api/games").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(dup)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_GAME"));
        dup.put("copyNumber", 2);
        mockMvc.perform(post("/api/games").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(dup)))
                .andExpect(status().isCreated());
        dup.put("rentalPrice", "-1");
        dup.put("copyNumber", 3);
        mockMvc.perform(post("/api/games").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(dup)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/rentals")).andExpect(status().isUnauthorized());
    }
}
