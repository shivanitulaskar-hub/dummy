package com.consoleadda.service.billing;

import java.math.BigDecimal;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Price data of one pricing plan, as plain values for the calculator.
 *
 * @param slabsByPlayerCount player count → (duration minutes → price). Must contain player count 1.
 *                           Entries for other counts override the base + additional-player rule.
 */
public record PlanPricing(
        Map<Integer, NavigableMap<Integer, BigDecimal>> slabsByPlayerCount,
        int extensionBlockMinutes,
        BigDecimal extensionPrice,
        BigDecimal additionalPlayerPrice,
        AdditionalPlayerBasis additionalPlayerBasis,
        int maxPlayers
) {

    public PlanPricing {
        NavigableMap<Integer, BigDecimal> base = slabsByPlayerCount.get(1);
        if (base == null || base.isEmpty()) {
            throw new IllegalArgumentException("A pricing plan needs at least one 1-player duration slab");
        }
        if (extensionBlockMinutes <= 0) {
            throw new IllegalArgumentException("Extension block must be positive");
        }
        if (maxPlayers < 1) {
            throw new IllegalArgumentException("Max players must be at least 1");
        }
    }
}
