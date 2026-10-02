package com.consoleadda.pricing;

import com.consoleadda.support.SessionTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PricingIntegrationTest extends SessionTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private Map<String, Object> plan(String name, String dayType, int priority, List<Map<String, Object>> rules) {
        Map<String, Object> p = new HashMap<>();
        p.put("name", name);
        p.put("dayType", dayType);
        p.put("activeFrom", LocalDate.now().minusDays(1).toString());
        p.put("active", true);
        p.put("priority", priority);
        p.put("minBillableMinutes", 60);
        p.put("billingBlockMinutes", 30);
        p.put("graceMinutes", 10);
        p.put("graceEveryBoundary", true);
        p.put("extensionBlockMinutes", 30);
        p.put("extensionPrice", 50);
        p.put("additionalPlayerPrice", 40);
        p.put("additionalPlayerBasis", "PER_SESSION");
        p.put("maxPlayers", 4);
        p.put("rules", rules);
        return p;
    }

    private static Map<String, Object> slab(int players, int minutes, String price) {
        return Map.of("playerCount", players, "durationMinutes", minutes, "price", price);
    }

    @Test
    void anyoneCanReadPricingAndQuotes() throws Exception {
        String op = operatorToken();
        mockMvc.perform(get("/api/pricing").header("Authorization", bearer(op)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Standard Weekday')].rules.length()").value(4));
        mockMvc.perform(get("/api/pricing/quote").param("minutes", "135").param("players", "3")
                        .header("Authorization", bearer(op)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billableMinutes").value(150))
                .andExpect(jsonPath("$.gamingAmount").value(445.0));
        mockMvc.perform(get("/api/pricing/quote").param("minutes", "-1").header("Authorization", bearer(op)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/pricing/quote").param("minutes", "abc").header("Authorization", bearer(op)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void operatorCannotChangePricing() throws Exception {
        mockMvc.perform(post("/api/pricing").header("Authorization", bearer(operatorToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(plan("Op plan", "SPECIAL", 1, List.of(slab(1, 60, "1"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPlansAreRejectedWithMeaningfulErrors() throws Exception {
        String admin = adminToken();
        mockMvc.perform(post("/api/pricing").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(plan("Neg " + UUID.randomUUID(), "SPECIAL", 1, List.of(slab(1, 60, "-5"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[0].field").value("rules[0].price"));
        mockMvc.perform(post("/api/pricing").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(plan("NoBase " + UUID.randomUUID(), "SPECIAL", 1, List.of(slab(2, 60, "100"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_BASE_SLAB"));
        mockMvc.perform(post("/api/pricing").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(plan("Dup " + UUID.randomUUID(), "SPECIAL", 1,
                                List.of(slab(1, 60, "90"), slab(1, 60, "80"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SLAB"));
        Map<String, Object> badGrace = plan("Grace " + UUID.randomUUID(), "SPECIAL", 1, List.of(slab(1, 60, "90")));
        badGrace.put("graceMinutes", 30);
        mockMvc.perform(post("/api/pricing").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(badGrace)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GRACE"));
    }

    @Test
    void specialPlanTakesPrecedenceAndChangesAreAudited() throws Exception {
        String admin = adminToken();
        String name = "Event " + UUID.randomUUID().toString().substring(0, 6);
        List<Map<String, Object>> rules = new ArrayList<>(List.of(slab(1, 60, "100.00"), slab(2, 60, "150.00")));
        String body = mockMvc.perform(post("/api/pricing").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(plan(name, "SPECIAL", 900, rules))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long planId = readJson(body).get("id").asLong();
        try {
            mockMvc.perform(get("/api/pricing/current").header("Authorization", bearer(admin)))
                    .andExpect(jsonPath("$.name").value(name));
            // Explicit 2-player slab overrides surcharge; 3 players = 100 + 2 × 40 (per session).
            mockMvc.perform(get("/api/pricing/quote").param("minutes", "60").param("players", "2")
                            .header("Authorization", bearer(admin)))
                    .andExpect(jsonPath("$.gamingAmount").value(150.0));
            mockMvc.perform(get("/api/pricing/quote").param("minutes", "60").param("players", "3")
                            .header("Authorization", bearer(admin)))
                    .andExpect(jsonPath("$.gamingAmount").value(180.0));

            // Update replaces slabs and is audited with old and new values.
            rules.add(slab(1, 120, "180.00"));
            mockMvc.perform(put("/api/pricing/{id}", planId).header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(json(plan(name, "SPECIAL", 900, rules))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rules.length()").value(3));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'PRICING_CHANGED' "
                    + "AND entity_id = ? AND old_value IS NOT NULL AND new_value IS NOT NULL", Integer.class,
                    String.valueOf(planId))).isEqualTo(1);

            // While a session runs on this plan, its prices are locked.
            long session = start(operatorToken(), createStation(), 1).get("id").asLong();
            mockMvc.perform(put("/api/pricing/{id}", planId).header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(json(plan(name, "SPECIAL", 900, rules))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PLAN_IN_USE"));
            clock.advance(Duration.ofMinutes(60));
            assertThat(complete(admin, session, "CASH").get("charges").get("totalAmount").decimalValue())
                    .isEqualByComparingTo("100");
            mockMvc.perform(delete("/api/pricing/{id}", planId).header("Authorization", bearer(admin)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PLAN_HAS_HISTORY"));
        } finally {
            // Deactivate so other tests keep using the standard plan.
            jdbc.update("UPDATE pricing_plans SET active = FALSE WHERE id = ?", planId);
        }
    }

    @Test
    void unusedPlanCanBeDeleted() throws Exception {
        String admin = adminToken();
        Map<String, Object> p = plan("Temp " + UUID.randomUUID(), "WEEKEND", 0, List.of(slab(1, 60, "99")));
        p.put("active", false);
        String body = mockMvc.perform(post("/api/pricing").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(p)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(delete("/api/pricing/{id}", readJson(body).get("id").asLong()).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
    }
}
