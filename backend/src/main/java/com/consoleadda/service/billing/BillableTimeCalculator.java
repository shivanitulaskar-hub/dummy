package com.consoleadda.service.billing;

import java.time.Duration;
import java.time.Instant;

/**
 * Converts played time into billable time. Pure function: no clock, no database.
 *
 * <pre>
 * actual   = whole minutes between start and end (partial minutes are never charged)
 * net      = max(0, actual − credit minutes)
 * billable = min                                       if net ≤ min + grace
 *          = smallest min + k·block (k ≥ 1)            with net ≤ that + grace   (grace at every boundary)
 *          = min + ceil((net − min) / block) · block   (grace only at the first boundary)
 * </pre>
 */
public final class BillableTimeCalculator {

    private BillableTimeCalculator() {
    }

    public static int actualMinutes(Instant startedAt, Instant endedAt) {
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("End time is before start time");
        }
        return Math.toIntExact(Duration.between(startedAt, endedAt).toMinutes());
    }

    public static int netMinutes(int actualMinutes, int creditMinutes) {
        if (actualMinutes < 0 || creditMinutes < 0) {
            throw new IllegalArgumentException("Minutes cannot be negative");
        }
        return Math.max(0, actualMinutes - creditMinutes);
    }

    public static int billableMinutes(int netMinutes, BillingTimeRules rules) {
        if (netMinutes < 0) {
            throw new IllegalArgumentException("Minutes cannot be negative");
        }
        int min = rules.minBillableMinutes();
        int block = rules.blockMinutes();
        int grace = rules.graceMinutes();
        if (netMinutes <= min + grace) {
            return min;
        }
        int beyond = rules.graceEveryBoundary() ? netMinutes - grace - min : netMinutes - min;
        int blocks = ceilDiv(beyond, block);
        return min + blocks * block;
    }

    static int ceilDiv(int value, int divisor) {
        return Math.floorDiv(value + divisor - 1, divisor);
    }
}
