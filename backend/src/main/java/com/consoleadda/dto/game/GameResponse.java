package com.consoleadda.dto.game;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A game disc. Purchase price and ROI are only filled in for admins. */
public record GameResponse(
        Long id,
        String name,
        int copyNumber,
        String platform,
        BigDecimal purchasePrice,
        LocalDate purchaseDate,
        BigDecimal rentalPrice,
        int rentalPeriodDays,
        BigDecimal lateFeePerDay,
        BigDecimal defaultDeposit,
        boolean availableForRental,
        boolean active,
        String notes,
        CurrentRental currentRental,
        Roi roi
) {

    public record CurrentRental(Long rentalId, String rentalNumber, String customerName, Instant expectedReturnAt,
                                boolean overdue) {
    }

    /** See {@code RentalCalculator.Roi}. */
    public record Roi(BigDecimal purchaseCost, BigDecimal revenue, long rentals, BigDecimal remaining,
                      BigDecimal roiPercent, Long breakEvenRentals, Long rentalsToRecover, boolean recovered) {
    }
}
