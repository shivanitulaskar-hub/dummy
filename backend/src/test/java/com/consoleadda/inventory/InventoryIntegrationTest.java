package com.consoleadda.inventory;

import com.consoleadda.support.FoodTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryIntegrationTest extends FoodTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private JsonNode stockRow(long foodItemId) throws Exception {
        String body = mockMvc.perform(get("/api/inventory/stock").header("Authorization", bearer(operatorToken())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode row : readJson(body)) {
            if (row.get("foodItemId").asLong() == foodItemId) {
                return row;
            }
        }
        throw new IllegalStateException("No stock row");
    }

    @Test
    void purchaseSaleWastageAdjustmentAndClosingStock() throws Exception {
        long food = createFoodItem("Cola", "40.00", 0, true);
        String admin = adminToken();
        String op = operatorToken();
        stockEntry(admin, food, "OPENING", 20).andExpect(status().isCreated());
        stockEntry(op, food, "PURCHASE", 12).andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(12));
        stockEntry(op, food, "WASTAGE", 2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(-2));
        stockEntry(admin, food, "ADJUSTMENT", -1).andExpect(status().isCreated());

        long session = start(op, createStation(), 1).get("id").asLong();
        JsonNode afterAdd = addItem(op, session, food, 3);
        long lineId = findLine(afterAdd, food).get("id").asLong();
        // Remove one of the three (reduce quantity) → 1 unit back in stock.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/sessions/{id}/items/{line}", session, lineId).header("Authorization", bearer(op))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk());

        JsonNode row = stockRow(food);
        assertThat(row.get("opening").asLong()).isEqualTo(20);
        assertThat(row.get("purchases").asLong()).isEqualTo(12);
        assertThat(row.get("sales").asLong()).isEqualTo(2);
        assertThat(row.get("wastage").asLong()).isEqualTo(2);
        assertThat(row.get("adjustments").asLong()).isEqualTo(-1);
        // closing = 20 + 12 − 2 − 2 − 1 = 27
        assertThat(row.get("closing").asLong()).isEqualTo(27);
        assertThat(row.get("currentStock").asInt()).isEqualTo(27);
        assertThat(stockOf(food)).isEqualTo(27);
        // Ledger and cached stock agree.
        assertThat(jdbc.queryForObject("SELECT SUM(quantity) FROM inventory_transactions WHERE food_item_id = ?",
                Long.class, food)).isEqualTo(27L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transactions WHERE food_item_id = ? "
                + "AND txn_type = 'SALE_REVERSAL'", Integer.class, food)).isEqualTo(1);
    }

    @Test
    void removingALineFromSessionReturnsStock() throws Exception {
        long food = createFoodItem("Juice", "50.00", 0, true);
        stockEntry(adminToken(), food, "OPENING", 10).andExpect(status().isCreated());
        String op = operatorToken();
        long session = start(op, createStation(), 1).get("id").asLong();
        long line = findLine(addItem(op, session, food, 4), food).get("id").asLong();
        assertThat(stockOf(food)).isEqualTo(6);
        mockMvc.perform(delete("/api/sessions/{id}/items/{line}", session, line).header("Authorization", bearer(op)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges.foodAmount").value(0.0));
        assertThat(stockOf(food)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'FOOD_ITEM_REMOVED'",
                Integer.class)).isPositive();
    }

    @Test
    void permissionsAndValidation() throws Exception {
        long food = createFoodItem("Water", "20.00", 0, true);
        String op = operatorToken();
        stockEntry(op, food, "ADJUSTMENT", 5).andExpect(status().isForbidden());
        stockEntry(op, food, "OPENING", 5).andExpect(status().isForbidden());
        stockEntry(op, food, "SALE", 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TXN_TYPE"));
        stockEntry(op, food, "PURCHASE", 0).andExpect(status().isBadRequest());
        stockEntry(op, food, "PURCHASE", -3).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUANTITY"));
        stockEntry(adminToken(), food, "ADJUSTMENT", 0).andExpect(status().isBadRequest());
        long untracked = createFoodItem("Fresh", "20.00", 0, false);
        stockEntry(op, untracked, "PURCHASE", 3).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_TRACKED"));
    }

    @Test
    void negativeStockCanBeDisallowed() throws Exception {
        long food = createFoodItem("Limited", "30.00", 0, true);
        stockEntry(adminToken(), food, "OPENING", 2).andExpect(status().isCreated());
        jdbc.update("UPDATE app_settings SET setting_value = 'false' WHERE setting_key = 'inventory.allow-negative-stock'");
        try {
            long session = start(operatorToken(), createStation(), 1).get("id").asLong();
            addItemRequest(operatorToken(), session, food, 3)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("only 2 left")));
            addItem(operatorToken(), session, food, 2);
            assertThat(stockOf(food)).isZero();
        } finally {
            jdbc.update("UPDATE app_settings SET setting_value = 'true' WHERE setting_key = 'inventory.allow-negative-stock'");
        }
    }
}
