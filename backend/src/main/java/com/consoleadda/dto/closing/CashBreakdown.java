package com.consoleadda.dto.closing;

import java.math.BigDecimal;

/** expected = opening + payments + deposits − refunds − deposit refunds (cash only). */
public record CashBreakdown(
        BigDecimal openingCash,
        BigDecimal cashPayments,
        BigDecimal cashDeposits,
        BigDecimal cashRefunds,
        BigDecimal cashDepositRefunds,
        BigDecimal expectedCash
) {
}
