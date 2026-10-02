package com.consoleadda.schema;

import com.consoleadda.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the Flyway migrations, seed data and database-level safety constraints. */
class SchemaIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationsAppliedAndSeedDataPresent() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                .isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForList("SELECT code FROM roles ORDER BY code", String.class))
                .containsExactly("ADMIN", "OPERATOR");
        assertThat(jdbc.queryForList("SELECT code FROM gaming_stations ORDER BY display_order", String.class))
                .containsExactly("PS5-01", "PS5-02", "PS5-03", "PS5-04", "PS5-05");
        assertThat(jdbc.queryForList(
                "SELECT price FROM pricing_rules r JOIN pricing_plans p ON p.id = r.pricing_plan_id "
                        + "WHERE p.name = 'Standard Weekday' AND r.player_count = 1 ORDER BY r.duration_minutes",
                BigDecimal.class))
                .containsExactly(new BigDecimal("90.00"), new BigDecimal("150.00"),
                        new BigDecimal("240.00"), new BigDecimal("330.00"));
        assertThat(jdbc.queryForObject("SELECT extension_price FROM pricing_plans WHERE name = 'Standard Weekday'",
                BigDecimal.class)).isEqualByComparingTo("45.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM food_items", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT gaming_credit_minutes FROM food_items WHERE name = 'Adda Virgin Mojito'",
                Integer.class)).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM game_master", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tax_settings WHERE enabled = TRUE", Integer.class)).isZero();
    }

    @Test
    void passwordsAreStoredAsBcryptHashes() {
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'admin'", String.class);
        assertThat(hash).startsWith("$2a$").doesNotContain(ADMIN_PASSWORD);
    }

    @Test
    void databaseRejectsSecondActiveSessionOnSameStation() {
        Long stationId = jdbc.queryForObject("SELECT id FROM gaming_stations WHERE code = 'PS5-05'", Long.class);
        Long planId = jdbc.queryForObject("SELECT id FROM pricing_plans WHERE name = 'Standard Weekday'", Long.class);

        insertSession(stationId, planId, "ACTIVE");
        assertThatThrownBy(() -> insertSession(stationId, planId, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_gaming_sessions_active_station");

        // Completed sessions on the same station are fine.
        jdbc.update("UPDATE gaming_sessions SET status = 'COMPLETED' WHERE station_id = ? AND status = 'ACTIVE'", stationId);
        insertSession(stationId, planId, "COMPLETED");
        insertSession(stationId, planId, "ACTIVE");
        jdbc.update("UPDATE gaming_sessions SET status = 'CANCELLED' WHERE station_id = ? AND status = 'ACTIVE'", stationId);
    }

    @Test
    void databaseRejectsNegativePricesAndQuantities() {
        assertThatThrownBy(() -> jdbc.update("UPDATE food_items SET selling_price = -1 WHERE name = 'Adda Maggi'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO inventory_transactions (food_item_id, txn_type, quantity, business_date) "
                        + "SELECT id, 'PURCHASE', -5, CURRENT_DATE FROM food_items WHERE name = 'Adda Maggi'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stationsEndpointListsActiveStationsInOrder() throws Exception {
        mockMvc.perform(get("/api/stations").header("Authorization", bearer(operatorToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("PS5-01"))
                .andExpect(jsonPath("$[4].code").value("PS5-05"))
                .andExpect(jsonPath("$[0].tvLabel").value("TV #1 (44\")"));
    }

    private void insertSession(Long stationId, Long planId, String status) {
        jdbc.update("INSERT INTO bills (bill_type, status) VALUES ('GAMING', 'OPEN')");
        Long billId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO gaming_sessions (station_id, bill_id, pricing_plan_id, player_count, started_at, status) "
                + "VALUES (?, ?, ?, 1, UTC_TIMESTAMP(6), ?)", stationId, billId, planId, status);
    }
}
