package com.consoleadda.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Helpers for food, inventory and bill tests. Each test creates its own menu items. */
public abstract class FoodTestSupport extends SessionTestSupport {

    protected long categoryId(String name) throws Exception {
        String body = mockMvc.perform(get("/api/food-categories").header("Authorization", bearer(operatorToken())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode c : readJson(body)) {
            if (c.get("name").asText().equals(name)) {
                return c.get("id").asLong();
            }
        }
        throw new IllegalStateException("No category " + name);
    }

    protected long createFoodItem(String namePrefix, String price, int creditMinutes, boolean trackInventory)
            throws Exception {
        Map<String, Object> item = new HashMap<>();
        item.put("name", namePrefix + " " + UUID.randomUUID().toString().substring(0, 8));
        item.put("categoryId", categoryId("Drinks"));
        item.put("sellingPrice", price);
        item.put("costPrice", "20.00");
        item.put("reorderLevel", 5);
        item.put("trackInventory", trackInventory);
        item.put("gamingCreditMinutes", creditMinutes);
        item.put("active", true);
        String body = mockMvc.perform(post("/api/food-items").header("Authorization", bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(item)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return readJson(body).get("id").asLong();
    }

    protected ResultActions stockEntry(String token, long foodItemId, String type, int quantity) throws Exception {
        return mockMvc.perform(post("/api/inventory/transactions").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("foodItemId", foodItemId, "type", type, "quantity", quantity))));
    }

    protected ResultActions addItemRequest(String token, long sessionId, long foodItemId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/sessions/{id}/items", sessionId).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("foodItemId", foodItemId, "quantity", quantity))));
    }

    protected JsonNode addItem(String token, long sessionId, long foodItemId, int quantity) throws Exception {
        return readJson(addItemRequest(token, sessionId, foodItemId, quantity)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    /** Current stock of an item according to the API. */
    protected int stockOf(long foodItemId) throws Exception {
        String body = mockMvc.perform(get("/api/food-items").param("includeInactive", "true")
                        .header("Authorization", bearer(adminToken())))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode i : readJson(body)) {
            if (i.get("id").asLong() == foodItemId) {
                return i.get("stockQuantity").asInt();
            }
        }
        throw new IllegalStateException("No item " + foodItemId);
    }

    protected JsonNode findLine(JsonNode session, long foodItemId) {
        for (JsonNode line : session.get("charges").get("lines")) {
            if (!line.get("foodItemId").isNull() && line.get("foodItemId").asLong() == foodItemId) {
                return line;
            }
        }
        return null;
    }
}
