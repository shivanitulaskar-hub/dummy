package com.consoleadda.customer;

import com.consoleadda.support.Phase4TestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerIntegrationTest extends Phase4TestSupport {

    private Map<String, Object> customer(String name, String mobile) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("mobile", mobile);
        return m;
    }

    @Test
    void createSearchAndEdit() throws Exception {
        String op = operatorToken();
        String mobile = uniqueMobile();
        String body = mockMvc.perform(post("/api/customers").header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(customer("Rahul Sharma", "+91 " + mobile.substring(0, 5) + " " + mobile.substring(5)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mobile").value(mobile))
                .andExpect(jsonPath("$.totalSessions").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = readJson(body).get("id").asLong();

        mockMvc.perform(post("/api/customers").header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(customer("Someone Else", mobile))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_MOBILE"));
        mockMvc.perform(post("/api/customers").header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(customer("", null))))
                .andExpect(status().isBadRequest());
        Map<String, Object> badEmail = customer("Mail", null);
        badEmail.put("email", "not-an-email");
        mockMvc.perform(post("/api/customers").header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(badEmail)))
                .andExpect(status().isBadRequest());

        // Completing a session for this mobile links the bill to the customer.
        long session = readJson(startRequest(op, createStation(), 1, mobile, null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        clock.advance(Duration.ofMinutes(60));
        String billNumber = complete(op, session, "CASH").get("billNumber").asText();

        for (String q : new String[]{"rahul sharma", mobile.substring(2, 8), billNumber}) {
            mockMvc.perform(get("/api/customers").param("q", q).header("Authorization", bearer(op)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(id))
                    .andExpect(jsonPath("$.content[0].totalSessions").value(1))
                    .andExpect(jsonPath("$.content[0].totalGamingMinutes").value(60));
        }

        Map<String, Object> edit = customer("Rahul S.", mobile);
        edit.put("email", "rahul@example.com");
        mockMvc.perform(put("/api/customers/{id}", id).header("Authorization", bearer(op))
                        .contentType(MediaType.APPLICATION_JSON).content(json(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rahul S."))
                .andExpect(jsonPath("$.email").value("rahul@example.com"))
                .andExpect(jsonPath("$.totalSessions").value(1)); // totals are never edited by hand
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'CUSTOMER_UPDATED' AND entity_id = ?",
                Integer.class, String.valueOf(id))).isEqualTo(1);
        mockMvc.perform(get("/api/customers/{id}", 999_999_999L).header("Authorization", bearer(op)))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyShowsSessionsBillsFoodAndRentals() throws Exception {
        String op = operatorToken();
        String mobile = uniqueMobile();
        long food = createFoodItem("History Cola", "40.00", 0, false);
        long game = createGame("History Game", "1500.00", "150.00", 7, "20.00", "0.00");

        long session = readJson(startRequest(op, createStation(), 2, mobile, "Priya")
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        addItem(op, session, food, 2);
        clock.advance(Duration.ofMinutes(60));
        JsonNode gaming = complete(op, session, "UPI");
        JsonNode walkIn = walkIn(op, food, 1, "CASH", mobile);
        JsonNode rental = rent(op, game, mobile, null, null, "CASH");

        long id = jdbc.queryForObject("SELECT id FROM customers WHERE mobile = ?", Long.class, mobile);
        String body = mockMvc.perform(get("/api/customers/{id}/history", id).header("Authorization", bearer(op)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode history = readJson(body);
        assertThat(history.get("customer").get("name").asText()).isEqualTo("Priya");
        assertThat(history.get("sessions")).hasSize(1);
        assertThat(history.get("sessions").get(0).get("billableMinutes").asInt()).isEqualTo(60);
        assertThat(history.get("bills")).hasSize(3);
        assertThat(history.get("bills").findValuesAsText("billType")).containsExactlyInAnyOrder("GAMING", "WALK_IN", "RENTAL");
        assertThat(history.get("food")).hasSize(1);
        assertThat(history.get("food").get(0).get("item").asText()).startsWith("History Cola");
        assertThat(history.get("food").get(0).get("quantity").asInt()).isEqualTo(3);
        assertThat(history.get("food").get(0).get("amount").decimalValue()).isEqualByComparingTo("120");
        assertThat(history.get("rentals")).hasSize(1);
        assertThat(history.get("rentals").get(0).get("rentalNumber").asText()).isEqualTo(rental.get("rentalNumber").asText());

        BigDecimal spent = gaming.get("charges").get("totalAmount").decimalValue()
                .add(walkIn.get("totalAmount").decimalValue()).add(rental.get("totalAmount").decimalValue());
        assertThat(history.get("customer").get("totalSpent").decimalValue()).isEqualByComparingTo(spent);
        assertThat(history.get("customer").get("totalSessions").asInt()).isEqualTo(1);
    }
}
