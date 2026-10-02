package com.consoleadda.service.billing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GamingPriceCalculatorTest {

    private static NavigableMap<Integer, BigDecimal> seedSlabs() {
        NavigableMap<Integer, BigDecimal> slabs = new TreeMap<>();
        slabs.put(60, new BigDecimal("90.00"));
        slabs.put(120, new BigDecimal("150.00"));
        slabs.put(180, new BigDecimal("240.00"));
        slabs.put(240, new BigDecimal("330.00"));
        return slabs;
    }

    private static PlanPricing plan(AdditionalPlayerBasis basis) {
        return new PlanPricing(Map.of(1, seedSlabs()), 30, new BigDecimal("45.00"), new BigDecimal("50.00"), basis, 4);
    }

    private static final PlanPricing PER_HOUR = plan(AdditionalPlayerBasis.PER_HOUR);
    private static final PlanPricing PER_SESSION = plan(AdditionalPlayerBasis.PER_SESSION);

    @ParameterizedTest(name = "1 player, {0} min → ₹{1}")
    @CsvSource({
            "60, 90.00",
            "90, 135.00",    // 1h + 30m extension
            "120, 150.00",   // 2h slab beats 1h + 2 × 45 = 180
            "150, 195.00",   // 2h + 45
            "180, 240.00",
            "210, 285.00",   // 3h + 45 (also = 2h + 3 × 45)
            "240, 330.00",
            "270, 375.00",   // beyond the largest slab: + 45 per 30 min
            "300, 420.00",
    })
    void singlePlayerSeedPricing(int minutes, String expected) {
        GamingCharge charge = GamingPriceCalculator.price(minutes, 1, PER_HOUR);
        assertThat(charge.total()).isEqualByComparingTo(expected);
        assertThat(charge.additionalPlayerAmount()).isEqualByComparingTo("0");
    }

    @ParameterizedTest(name = "{1} players, {0} min, per hour → ₹{2}")
    @CsvSource({
            "60, 2, 140.00",   // 90 + 1 × 50
            "60, 3, 190.00",   // 90 + 2 × 50  (spec live-card example)
            "60, 4, 240.00",
            "90, 3, 285.00",   // 135 + 2 × 50 × 1.5
            "120, 3, 350.00",  // 150 + 2 × 50 × 2
            "150, 3, 445.00",  // 195 + 2 × 50 × 2.5
            "270, 3, 825.00",  // 375 + 2 × 50 × 4.5
    })
    void multiplayerPerHour(int minutes, int players, String expected) {
        assertThat(GamingPriceCalculator.price(minutes, players, PER_HOUR).total()).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "{1} players, {0} min, per session → ₹{2}")
    @CsvSource({"60, 3, 190.00", "120, 3, 250.00", "150, 2, 245.00"})
    void multiplayerPerSession(int minutes, int players, String expected) {
        assertThat(GamingPriceCalculator.price(minutes, players, PER_SESSION).total()).isEqualByComparingTo(expected);
    }

    @Test
    void explicitPlayerSlabsOverrideTheSurcharge() {
        NavigableMap<Integer, BigDecimal> twoPlayer = new TreeMap<>(Map.of(60, new BigDecimal("130.00"),
                120, new BigDecimal("220.00")));
        Map<Integer, NavigableMap<Integer, BigDecimal>> slabs = new HashMap<>();
        slabs.put(1, seedSlabs());
        slabs.put(2, twoPlayer);
        PlanPricing plan = new PlanPricing(slabs, 30, new BigDecimal("45.00"), new BigDecimal("50.00"),
                AdditionalPlayerBasis.PER_HOUR, 4);

        GamingCharge two = GamingPriceCalculator.price(60, 2, plan);
        assertThat(two.total()).isEqualByComparingTo("130.00");
        assertThat(two.additionalPlayerAmount()).isEqualByComparingTo("0");
        assertThat(GamingPriceCalculator.price(150, 2, plan).total()).isEqualByComparingTo("265.00"); // 220 + 45
        // 3 players has no explicit slabs → base + surcharge.
        assertThat(GamingPriceCalculator.price(60, 3, plan).total()).isEqualByComparingTo("190.00");
    }

    @Test
    void largerPlayerTiersAreConfigurable() {
        PlanPricing sixSeats = new PlanPricing(Map.of(1, seedSlabs()), 30, new BigDecimal("45.00"),
                new BigDecimal("50.00"), AdditionalPlayerBasis.PER_SESSION, 6);
        assertThat(GamingPriceCalculator.price(60, 6, sixSeats).total()).isEqualByComparingTo("340.00");
    }

    @Test
    void playerCountOutsidePlanLimitsIsRejected() {
        assertThatThrownBy(() -> GamingPriceCalculator.price(60, 5, PER_HOUR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GamingPriceCalculator.price(60, 0, PER_HOUR)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pricingNeverDecreasesAsTimeIncreases() {
        BigDecimal previous = BigDecimal.ZERO;
        for (int minutes = 60; minutes <= 600; minutes += 30) {
            BigDecimal price = GamingPriceCalculator.price(minutes, 1, PER_HOUR).total();
            assertThat(price).as("price at %d min", minutes).isGreaterThanOrEqualTo(previous);
            previous = price;
        }
    }

    @Test
    void explanationDescribesTheCalculation() {
        assertThat(GamingPriceCalculator.price(150, 3, PER_HOUR).explanation())
                .isEqualTo("2h slab ₹150 + 1 × 30m extension ₹45 + 2 extra player(s) × ₹50/hr × 2h 30m");
    }

    @Test
    void planWithoutSinglePlayerSlabsIsRejected() {
        assertThatThrownBy(() -> new PlanPricing(Map.of(), 30, BigDecimal.TEN, BigDecimal.ZERO,
                AdditionalPlayerBasis.PER_HOUR, 4)).isInstanceOf(IllegalArgumentException.class);
    }
}
