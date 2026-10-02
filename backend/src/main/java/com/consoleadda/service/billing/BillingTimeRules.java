package com.consoleadda.service.billing;

/**
 * Configurable time-rounding rules of a pricing plan.
 *
 * @param minBillableMinutes  minimum charge (e.g. 60: any session is billed at least 1 hour)
 * @param blockMinutes        billing increment after the minimum (e.g. 30)
 * @param graceMinutes        extra minutes ignored past a boundary (e.g. 10)
 * @param graceEveryBoundary  true: grace applies at every boundary (1:40 → 1:30);
 *                            false: only at the first boundary (1:40 → 2:00)
 */
public record BillingTimeRules(int minBillableMinutes, int blockMinutes, int graceMinutes, boolean graceEveryBoundary) {

    public BillingTimeRules {
        if (minBillableMinutes <= 0 || blockMinutes <= 0) {
            throw new IllegalArgumentException("Minimum and block minutes must be positive");
        }
        if (graceMinutes < 0 || graceMinutes >= blockMinutes) {
            throw new IllegalArgumentException("Grace minutes must be >= 0 and smaller than the billing block");
        }
    }
}
