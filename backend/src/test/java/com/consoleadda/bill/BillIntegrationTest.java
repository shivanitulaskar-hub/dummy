package com.consoleadda.bill;

import com.consoleadda.support.FoodTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillIntegrationTest extends FoodTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private JsonNode walkIn(String token, Map<String, Object> request, String key) throws Exception {
        var builder = post("/api/bills").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(request));
        if (key != null) {
            builder.header("Idempotency-Key", key);
        }
        return readJson(mockMvc.perform(builder).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void walkInFoodSaleIsBilledPaidAndDeductsStock() throws Exception {
        long food = createFoodItem("Iced Tea", "45.00", 15, true);
        stockEntry(adminToken(), food, "OPENING", 10).andExpect(status().isCreated());
        String mobile = "97" + String.format("%08d", System.nanoTime() % 100_000_000L);
        JsonNode bill = walkIn(operatorToken(), Map.of(
                "customerName", "Priya", "customerMobile", mobile, "paymentMode", "UPI",
                "items", List.of(Map.of("foodItemId", food, "quantity", 2), Map.of("foodItemId", food, "quantity", 1))),
                null);
        assertThat(bill.get("billType").asText()).isEqualTo("WALK_IN");
        assertThat(bill.get("status").asText()).isEqualTo("PAID");
        assertThat(bill.get("billNumber").asText()).matches("CA-\\d{4}-\\d{6}");
        assertThat(bill.get("lines")).hasSize(1); // merged
        assertThat(bill.get("lines").get(0).get("quantity").asInt()).isEqualTo(3);
        assertThat(bill.get("totalAmount").decimalValue()).isEqualByComparingTo("135");
        assertThat(bill.get("gaming").isNull()).isTrue();
        assertThat(bill.get("payments").get(0).get("mode").asText()).isEqualTo("UPI");
        assertThat(stockOf(food)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT total_spent FROM customers WHERE mobile = ?", BigDecimal.class, mobile))
                .isEqualByComparingTo("135");
        assertThat(jdbc.queryForObject("SELECT total_sessions FROM customers WHERE mobile = ?", Integer.class, mobile))
                .isZero();
    }

    @Test
    void walkInRetryWithSameKeyDoesNotChargeTwice() throws Exception {
        long food = createFoodItem("Lassi", "60.00", 0, true);
        Map<String, Object> req = Map.of("paymentMode", "CASH", "items", List.of(Map.of("foodItemId", food, "quantity", 1)));
        String key = "walkin-" + food;
        JsonNode first = walkIn(operatorToken(), req, key);
        JsonNode retry = walkIn(operatorToken(), req, key);
        assertThat(retry.get("id").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(stockOf(food)).isEqualTo(-1); // sold once (negative stock allowed by default)
    }

    @Test
    void walkInValidation() throws Exception {
        mockMvc.perform(post("/api/bills").header("Authorization", bearer(operatorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentMode", "CASH", "items", List.of()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/bills").header("Authorization", bearer(operatorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentMode", "CASH",
                                "items", List.of(Map.of("foodItemId", 1, "quantity", -2))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void billsCanBeSearchedAndInvoiced() throws Exception {
        String op = operatorToken();
        String mobile = "96" + String.format("%08d", System.nanoTime() % 100_000_000L);
        String body = startRequest(op, createStation(), 2, mobile, "Vikram Search")
                .andReturn().getResponse().getContentAsString();
        long session = readJson(body).get("id").asLong();
        clock.advance(Duration.ofMinutes(60));
        JsonNode done = complete(op, session, "CASH");
        String billNumber = done.get("billNumber").asText();
        long billId = done.get("billId").asLong();

        for (String q : List.of(billNumber, mobile, "vikram search")) {
            mockMvc.perform(get("/api/bills").param("q", q).header("Authorization", bearer(op)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].billNumber").value(billNumber))
                    .andExpect(jsonPath("$.content[0].paymentMode").value("CASH"));
        }

        mockMvc.perform(get("/api/bills/{id}/invoice", billId).header("Authorization", bearer(op)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business.name").value("CONSOLE ADDA"))
                .andExpect(jsonPath("$.business.tagline").value("PS5 GAMING CAFE"))
                .andExpect(jsonPath("$.footer").value("Thank you for gaming with us!"))
                .andExpect(jsonPath("$.bill.billNumber").value(billNumber))
                .andExpect(jsonPath("$.bill.customerMobile").value(mobile))
                .andExpect(jsonPath("$.bill.gaming.playerCount").value(2))
                .andExpect(jsonPath("$.bill.gaming.billableMinutes").value(60))
                .andExpect(jsonPath("$.bill.totalAmount").value(140.0))
                .andExpect(jsonPath("$.bill.payments[0].mode").value("CASH"));
    }

    @Test
    void adminCancelsPaidBillWithRefundAndStockReturn() throws Exception {
        long food = createFoodItem("Chips", "30.00", 0, true);
        stockEntry(adminToken(), food, "OPENING", 5).andExpect(status().isCreated());
        JsonNode bill = walkIn(operatorToken(), Map.of("paymentMode", "CARD",
                "items", List.of(Map.of("foodItemId", food, "quantity", 2))), null);
        long id = bill.get("id").asLong();
        assertThat(stockOf(food)).isEqualTo(3);

        mockMvc.perform(post("/api/bills/{id}/cancel", id).header("Authorization", bearer(operatorToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Wrong order"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/bills/{id}/cancel", id).header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Wrong order"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Wrong order"))
                .andExpect(jsonPath("$.payments[1].type").value("REFUND"))
                .andExpect(jsonPath("$.payments[1].mode").value("CARD"))
                .andExpect(jsonPath("$.payments[1].amount").value(60.0));
        assertThat(stockOf(food)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'BILL_CANCELLED' AND entity_id = ?",
                Integer.class, String.valueOf(id))).isEqualTo(1);
        mockMvc.perform(post("/api/bills/{id}/cancel", id).header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "again"))))
                .andExpect(status().isConflict());
    }

    /**
     * Operators adding food while another completes the bill: every unit that left stock must be on the paid bill
     * (or the add must fail cleanly) — never sold-but-unbilled.
     */
    @Test
    void addingFoodWhileCompletingNeverLosesItems() throws Exception {
        long food = createFoodItem("Race Soda", "10.00", 0, true);
        String op = operatorToken();
        long session = start(op, createStation(), 1).get("id").asLong();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(pool.submit((Callable<MvcResult>) () -> {
                go.await();
                return addItemRequest(op, session, food, 1).andReturn();
            }));
        }
        futures.add(pool.submit((Callable<MvcResult>) () -> {
            go.await();
            return completeRequest(op, session, "CASH", null).andReturn();
        }));
        go.countDown();
        for (Future<MvcResult> f : futures) {
            int code = f.get(60, TimeUnit.SECONDS).getResponse().getStatus();
            assertThat(code).isIn(200, 201, 409);
        }
        pool.shutdown();
        Long billId = jdbc.queryForObject("SELECT bill_id FROM gaming_sessions WHERE id = ?", Long.class, session);
        Integer onBill = jdbc.queryForObject("SELECT COALESCE(SUM(quantity), 0) FROM bill_items WHERE bill_id = ? "
                + "AND food_item_id = ?", Integer.class, billId, food);
        Integer sold = jdbc.queryForObject("SELECT -COALESCE(SUM(quantity), 0) FROM inventory_transactions "
                + "WHERE food_item_id = ? AND txn_type IN ('SALE', 'SALE_REVERSAL')", Integer.class, food);
        assertThat(sold).as("units sold = units on bill").isEqualTo(onBill);
        BigDecimal foodAmount = jdbc.queryForObject("SELECT food_amount FROM bills WHERE id = ?", BigDecimal.class, billId);
        assertThat(foodAmount).isEqualByComparingTo(BigDecimal.valueOf(onBill * 10L));
    }
}
