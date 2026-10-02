package com.consoleadda.service.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Game rental money rules. Pure functions (see docs/ARCHITECTURE.md A14, A15).
 * <ul>
 *   <li>Rental price: the game's price covers one period ({@code periodDays}); longer rentals pay per started
 *   period, e.g. 10 days on a 7-day ₹199 price = 2 × ₹199.</li>
 *   <li>Late days: none while the disc is back within the grace period after the expected return time;
 *   after that every started 24 hours past the expected return time counts as one day.</li>
 *   <li>ROI per disc: revenue against purchase cost.</li>
 * </ul>
 */
public final class RentalCalculator {

    private static final long SECONDS_PER_DAY = Duration.ofDays(1).toSeconds();

    private RentalCalculator() {
    }

    public static BigDecimal rentalPrice(BigDecimal pricePerPeriod, int periodDays, int rentalDays) {
        if (periodDays <= 0 || rentalDays <= 0) {
            throw new IllegalArgumentException("Rental and period days must be positive");
        }
        long periods = Math.ceilDiv(rentalDays, periodDays);
        return Money.round(pricePerPeriod.multiply(BigDecimal.valueOf(periods)));
    }

    public static int lateDays(Instant expectedReturnAt, Instant returnedAt, Duration grace) {
        if (!returnedAt.isAfter(expectedReturnAt.plus(grace))) {
            return 0;
        }
        long secondsLate = Duration.between(expectedReturnAt, returnedAt).toSeconds();
        return Math.toIntExact(Math.ceilDiv(secondsLate, SECONDS_PER_DAY));
    }

    public static BigDecimal lateFee(int lateDays, BigDecimal feePerDay) {
        return Money.round(feePerDay.multiply(BigDecimal.valueOf(lateDays)));
    }

    /**
     * @param revenue       rental charges + late fees collected for this disc (cancelled rentals excluded)
     * @param remaining     cost not yet recovered (0 once recovered)
     * @param roiPercent    (revenue − cost) / cost × 100; null when the purchase cost is 0
     * @param breakEvenRentals rentals at the current price needed to recover the full cost; null when price is 0
     * @param rentalsToRecover further rentals needed from now; null when price is 0
     */
    public record Roi(BigDecimal purchaseCost, BigDecimal revenue, long rentals, BigDecimal remaining,
                      BigDecimal roiPercent, Long breakEvenRentals, Long rentalsToRecover, boolean recovered) {
    }

    public static Roi roi(BigDecimal purchaseCost, BigDecimal currentRentalPrice, BigDecimal revenue, long rentals) {
        BigDecimal cost = Money.round(purchaseCost);
        BigDecimal earned = Money.round(revenue);
        BigDecimal remaining = cost.subtract(earned).max(Money.ZERO);
        BigDecimal roiPercent = cost.signum() == 0 ? null
                : earned.subtract(cost).multiply(Money.HUNDRED).divide(cost, Money.SCALE, RoundingMode.HALF_UP);
        Long breakEven = null;
        Long toRecover = null;
        if (currentRentalPrice.signum() > 0) {
            breakEven = cost.divide(currentRentalPrice, 0, RoundingMode.CEILING).longValueExact();
            toRecover = remaining.divide(currentRentalPrice, 0, RoundingMode.CEILING).longValueExact();
        }
        return new Roi(cost, earned, rentals, remaining, roiPercent, breakEven, toRecover, remaining.signum() == 0);
    }
}
