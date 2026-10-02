package com.consoleadda.dto.closing;

import java.math.BigDecimal;

/** Day totals as shown on the closing screen. See {@code SalesFigures} for how each figure is defined. */
public record ClosingFigures(
        int billCount,
        int sessionCount,
        int customerCount,
        int gamingMinutes,
        int foodUnitsSold,
        BigDecimal gamingRevenue,
        BigDecimal foodRevenue,
        BigDecimal rentalRevenue,
        BigDecimal grossSales,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal roundOffTotal,
        BigDecimal totalRevenue,
        BigDecimal cash,
        BigDecimal upi,
        BigDecimal card,
        BigDecimal other,
        BigDecimal refundTotal,
        BigDecimal depositTotal,
        BigDecimal depositRefundTotal
) {
}
