package com.consoleadda.service.billing;

import java.math.BigDecimal;

/**
 * Result of pricing a billable duration.
 *
 * @param baseAmount           price of the duration for the slab set used
 * @param additionalPlayerAmount surcharge for players beyond one (zero if explicit slabs were used)
 * @param total                baseAmount + additionalPlayerAmount
 * @param explanation          human-readable breakdown, e.g. "2h slab ₹150 + 1 × 30m extension ₹45"
 */
public record GamingCharge(BigDecimal baseAmount, BigDecimal additionalPlayerAmount, BigDecimal total,
                           String explanation) {
}
