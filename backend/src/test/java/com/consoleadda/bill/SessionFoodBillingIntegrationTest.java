package com.consoleadda.bill;

import com.consoleadda.support.FoodTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SessionFoodBillingIntegrationTest extends FoodTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private long seededItem(String name) {
        return jdbc.queryForObject("SELECT id FROM food_items WHERE name = ?", Long.class, name);
    }

    private long createDiscount(String type, String value, String maxAmount, LocalDate validTo) throws Exception {
        Map<String, Object> d = new HashMap<>();
        d.put("code", "T" + UUID.randomUUID().toString().substring(0, 8));
        d.put("name", "Test " + type + " " + value);
        d.put("discountType", type);
        d.put("value", value);
        d.put("maxAmount", maxAmount);
        d.put("validTo", validTo == null ? null : validTo.toString());
        d.put("active", true);
        String body = mockMvc.perform(post("/api/discounts").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(d)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return readJson(body).get("id").asLong();
    }

    private JsonNode completeWith(long sessionId, Map<String, Object> body) throws Exception {
        return readJson(mockMvc.perform(post("/api/sessions/{id}/complete", sessionId)
                        .header("Authorization", bearer(operatorToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void foodIsAddedToTheGamingBill() throws Exception {
        String op = operatorToken();
        long session = start(op, createStation(), 1).get("id").asLong();
        long shawarma = seededItem("Chicken Shawarma");
        long momos = seededItem("Chicken Momos Fried");
        addItem(op, session, shawarma, 1);
        JsonNode s = addItem(op, session, shawarma, 1); // same item merges into one line × 2
        s = addItem(op, session, momos, 1);
        assertThat(findLine(s, shawarma).get("quantity").asInt()).isEqualTo(2);
        assertThat(s.get("charges").get("foodAmount").decimalValue()).isEqualByComparingTo("430"); // 240 + 190

        clock.advance(Duration.ofMinutes(60));
        JsonNode done = complete(op, session, "CASH");
        assertThat(done.get("charges").get("gamingAmount").decimalValue()).isEqualByComparingTo("90");
        assertThat(done.get("charges").get("totalAmount").decimalValue()).isEqualByComparingTo("520");
        long billId = done.get("billId").asLong();
        assertThat(jdbc.queryForObject("SELECT food_amount FROM bills WHERE id = ?", BigDecimal.class, billId))
                .isEqualByComparingTo("430.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bill_items WHERE bill_id = ?", Integer.class, billId))
                .isEqualTo(3);
        // Food cannot be added after the bill is paid.
        addItemRequest(op, session, shawarma, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_ACTIVE"));
    }

    @Test
    void mojitoGivesFifteenMinuteCreditCappedPerSession() throws Exception {
        String op = operatorToken();
        long session = start(op, createStation(), 1).get("id").asLong();
        long mojito = seededItem("Adda Virgin Mojito");
        JsonNode s = addItem(op, session, mojito, 1);
        assertThat(s.get("charges").get("creditMinutes").asInt()).isEqualTo(15);
        s = addItem(op, session, mojito, 1); // second mojito: still capped at 15
        assertThat(s.get("charges").get("creditMinutes").asInt()).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT SUM(minutes) FROM session_time_credits WHERE session_id = ?",
                Integer.class, session)).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT reason FROM session_time_credits WHERE session_id = ?",
                String.class, session)).contains("Adda Virgin Mojito");

        clock.advance(Duration.ofMinutes(75)); // 1h 15m played − 15m credit → billed 1h
        JsonNode done = complete(op, session, "UPI");
        assertThat(done.get("charges").get("actualMinutes").asInt()).isEqualTo(75);
        assertThat(done.get("charges").get("billableMinutes").asInt()).isEqualTo(60);
        // The credit is not money: gaming ₹90 + 2 mojitos ₹118 = ₹208.
        assertThat(done.get("charges").get("totalAmount").decimalValue()).isEqualByComparingTo("208");
    }

    @Test
    void removingTheMojitoRemovesTheCredit() throws Exception {
        String op = operatorToken();
        long session = start(op, createStation(), 1).get("id").asLong();
        long mojito = seededItem("Adda Virgin Mojito");
        long line = findLine(addItem(op, session, mojito, 1), mojito).get("id").asLong();
        mockMvc.perform(delete("/api/sessions/{id}/items/{line}", session, line).header("Authorization", bearer(op)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges.creditMinutes").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM session_time_credits WHERE session_id = ?",
                Integer.class, session)).isZero();
    }

    @Test
    void sessionWithFoodCannotBeCancelledUntilFoodIsRemoved() throws Exception {
        String op = operatorToken();
        long session = start(op, createStation(), 1).get("id").asLong();
        addItem(op, session, seededItem("Adda Maggi"), 1);
        mockMvc.perform(post("/api/sessions/{id}/cancel", session).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "x"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_HAS_ITEMS"));
    }

    @Test
    void discountsArePreviewedAppliedAndAudited() throws Exception {
        String op = operatorToken();
        long percent = createDiscount("PERCENTAGE", "10", "20", null);
        long session = start(op, createStation(), 1).get("id").asLong();
        addItem(op, session, seededItem("Chicken Momos Steamed"), 2); // 340
        clock.advance(Duration.ofMinutes(120));                       // gaming 150 → subtotal 490
        mockMvc.perform(post("/api/sessions/{id}/end", session).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("discountId", percent))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges.subtotal").value(490.0))
                .andExpect(jsonPath("$.charges.discountAmount").value(20.0)) // 10% = 49, capped at 20
                .andExpect(jsonPath("$.charges.totalAmount").value(470.0));

        Map<String, Object> body = new HashMap<>();
        body.put("paymentMode", "CASH");
        body.put("discountId", percent);
        JsonNode done = completeWith(session, body);
        long billId = done.get("billId").asLong();
        Map<String, Object> bill = jdbc.queryForMap("SELECT subtotal, discount_amount, discount_id, discount_reason, "
                + "total_amount FROM bills WHERE id = ?", billId);
        assertThat((BigDecimal) bill.get("subtotal")).isEqualByComparingTo("490");
        assertThat((BigDecimal) bill.get("discount_amount")).isEqualByComparingTo("20");
        assertThat((BigDecimal) bill.get("total_amount")).isEqualByComparingTo("470");
        assertThat(((Number) bill.get("discount_id")).longValue()).isEqualTo(percent);
        // Item prices on the bill are unchanged by the discount.
        assertThat(jdbc.queryForObject("SELECT unit_price FROM bill_items WHERE bill_id = ? AND item_type = 'FOOD'",
                BigDecimal.class, billId)).isEqualByComparingTo("170");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'DISCOUNT_APPLIED' AND entity_id = ?",
                Integer.class, String.valueOf(billId))).isEqualTo(1);
    }

    @Test
    void rePreviewWithDiscountKeepsTheFrozenEndTime() throws Exception {
        String op = operatorToken();
        long flat = createDiscount("FLAT", "10", null, null);
        long session = start(op, createStation(), 1).get("id").asLong();
        clock.advance(Duration.ofMinutes(65));
        String first = mockMvc.perform(post("/api/sessions/{id}/end", session).header("Authorization", bearer(op)))
                .andReturn().getResponse().getContentAsString();
        String frozen = readJson(first).get("charges").get("calculatedAt").asText();
        clock.advance(Duration.ofMinutes(10)); // now 1h 15m: re-preview must still bill 1h 05m → 1h
        Map<String, Object> body = new HashMap<>();
        body.put("discountId", flat);
        body.put("endedAt", frozen);
        mockMvc.perform(post("/api/sessions/{id}/end", session).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges.calculatedAt").value(frozen))
                .andExpect(jsonPath("$.charges.billableMinutes").value(60))
                .andExpect(jsonPath("$.charges.totalAmount").value(80.0));
    }

    @Test
    void expiredDiscountIsRejected() throws Exception {
        long expired = createDiscount("FLAT", "50", null, LocalDate.now().minusDays(10));
        long session = start(operatorToken(), createStation(), 1).get("id").asLong();
        Map<String, Object> body = new HashMap<>();
        body.put("paymentMode", "CASH");
        body.put("discountId", expired);
        mockMvc.perform(post("/api/sessions/{id}/complete", session).header("Authorization", bearer(operatorToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DISCOUNT"));
    }

    @Test
    void gstIsAppliedPerCategoryAndStoredOnTheBill() throws Exception {
        String admin = adminToken();
        try {
            mockMvc.perform(put("/api/settings/tax/GAMING").header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("enabled", true, "ratePercent", "18", "priceMode", "EXCLUSIVE",
                                    "hsnSacCode", "998439"))))
                    .andExpect(status().isOk());
            mockMvc.perform(put("/api/settings/tax/FOOD").header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("enabled", true, "ratePercent", "5", "priceMode", "INCLUSIVE",
                                    "hsnSacCode", "996331"))))
                    .andExpect(status().isOk());
            String op = operatorToken();
            long session = start(op, createStation(), 1).get("id").asLong();
            addItem(op, session, seededItem("Veg Cheese Shawarma"), 1); // 120 incl. 5% → tax 5.71
            clock.advance(Duration.ofMinutes(60));                       // 90 + 18% → tax 16.20
            JsonNode done = complete(op, session, "CARD");
            assertThat(done.get("charges").get("taxAmount").decimalValue()).isEqualByComparingTo("21.91");
            // total = 90 + 16.20 + 120 = 226.20 → rounded 226.00
            assertThat(done.get("charges").get("totalAmount").decimalValue()).isEqualByComparingTo("226");
            assertThat(done.get("charges").get("roundOff").decimalValue()).isEqualByComparingTo("-0.20");
            long billId = done.get("billId").asLong();
            assertThat(jdbc.queryForList("SELECT CONCAT(tax_category, ':', rate_percent, ':', tax_amount) FROM bill_tax_lines "
                    + "WHERE bill_id = ? ORDER BY tax_category", String.class, billId))
                    .containsExactly("FOOD:5.00:5.71", "GAMING:18.00:16.20");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'GST_CHANGED'", Integer.class))
                    .isGreaterThanOrEqualTo(2);
            // Operators cannot change GST.
            mockMvc.perform(put("/api/settings/tax/FOOD").header("Authorization", bearer(op))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("enabled", false, "ratePercent", "0", "priceMode", "EXCLUSIVE"))))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("UPDATE tax_settings SET enabled = FALSE");
        }
    }
}
