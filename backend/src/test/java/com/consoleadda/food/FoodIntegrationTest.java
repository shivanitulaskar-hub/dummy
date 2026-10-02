package com.consoleadda.food;

import com.consoleadda.support.FoodTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FoodIntegrationTest extends FoodTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void menuShowsSeededItemsAndHidesCostFromOperators() throws Exception {
        mockMvc.perform(get("/api/food-items").header("Authorization", bearer(operatorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Chicken Shawarma')].sellingPrice").value(120.0))
                .andExpect(jsonPath("$[?(@.name == 'Adda Virgin Mojito')].gamingCreditMinutes").value(15))
                .andExpect(jsonPath("$[?(@.name == 'Chicken Shawarma')].costPrice").value((Object) null));
        mockMvc.perform(get("/api/food-items").header("Authorization", bearer(adminToken())))
                .andExpect(jsonPath("$[?(@.name == 'Chicken Shawarma')].costPrice").value(0.0));
    }

    private Map<String, Object> item(String name, Object price) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("categoryId", categoryId("Snacks"));
        m.put("sellingPrice", price);
        m.put("costPrice", "10");
        m.put("reorderLevel", 3);
        m.put("trackInventory", true);
        m.put("gamingCreditMinutes", 0);
        m.put("active", true);
        return m;
    }

    @Test
    void adminManagesMenuAndPriceChangesAreAudited() throws Exception {
        String admin = adminToken();
        String name = "Nachos " + System.nanoTime();
        String body = mockMvc.perform(post("/api/food-items").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(item(name, "99.00"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = readJson(body).get("id").asLong();

        mockMvc.perform(put("/api/food-items/{id}", id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(item(name, "109.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellingPrice").value(109.0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'FOOD_PRICE_CHANGED' AND entity_id = ?",
                Integer.class, String.valueOf(id))).isEqualTo(1);

        mockMvc.perform(post("/api/food-items").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(item(name, "50"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));
        mockMvc.perform(post("/api/food-items").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(item("Bad " + System.nanoTime(), "-5"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[0].field").value("sellingPrice"));
    }

    @Test
    void operatorCannotChangeMenu() throws Exception {
        mockMvc.perform(post("/api/food-items").header("Authorization", bearer(operatorToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(item("Op " + System.nanoTime(), "10"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/food-categories").header("Authorization", bearer(operatorToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Op cat"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void inactiveItemCannotBeSold() throws Exception {
        long food = createFoodItem("Retired", "30.00", 0, false);
        Map<String, Object> m = item("Retired-" + food, "30.00");
        m.put("active", false);
        mockMvc.perform(put("/api/food-items/{id}", food).header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(m)))
                .andExpect(status().isOk());
        long session = start(operatorToken(), createStation(), 1).get("id").asLong();
        addItemRequest(operatorToken(), session, food, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_INACTIVE"));
    }
}
