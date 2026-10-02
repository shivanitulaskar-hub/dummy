package com.consoleadda.dto.pricing;

import java.math.BigDecimal;

public record PriceQuoteResponse(
        Long planId,
        String planName,
        int playedMinutes,
        int creditMinutes,
        int billableMinutes,
        int playerCount,
        BigDecimal baseAmount,
        BigDecimal additionalPlayerAmount,
        BigDecimal gamingAmount,
        String explanation
) {
}
