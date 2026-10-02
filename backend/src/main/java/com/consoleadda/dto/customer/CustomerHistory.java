package com.consoleadda.dto.customer;

import com.consoleadda.dto.bill.BillSummary;
import com.consoleadda.dto.rental.RentalResponse;
import com.consoleadda.entity.SessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Most recent activity of one customer (each list capped at {@code CustomerService.HISTORY_LIMIT}). */
public record CustomerHistory(
        CustomerResponse customer,
        List<Session> sessions,
        List<BillSummary> bills,
        List<FoodPurchase> food,
        List<RentalResponse> rentals
) {

    public record Session(Long id, String stationCode, int playerCount, Instant startedAt, Instant endedAt,
                          Integer billableMinutes, BigDecimal gamingAmount, SessionStatus status, Long billId,
                          String billNumber) {
    }

    /** Food bought on paid bills, per item. */
    public record FoodPurchase(String item, long quantity, BigDecimal amount, Instant lastBoughtAt) {
    }
}
