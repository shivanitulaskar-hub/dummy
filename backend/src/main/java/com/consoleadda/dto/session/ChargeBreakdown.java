package com.consoleadda.dto.session;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Bill calculation for a session at {@code calculatedAt} (live estimate, END preview or final bill). */
public record ChargeBreakdown(
        Instant calculatedAt,
        int actualMinutes,
        int creditMinutes,
        int billableMinutes,
        BigDecimal gamingAmount,
        String gamingExplanation,
        BigDecimal foodAmount,
        BigDecimal subtotal,
        String discountName,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal roundOff,
        BigDecimal totalAmount,
        List<ChargeLine> lines
) {
}
