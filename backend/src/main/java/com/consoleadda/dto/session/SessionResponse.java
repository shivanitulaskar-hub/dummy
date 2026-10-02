package com.consoleadda.dto.session;

import com.consoleadda.entity.PaymentMode;
import com.consoleadda.entity.SessionStatus;

import java.time.Instant;

public record SessionResponse(
        Long id,
        Long stationId,
        String stationCode,
        String customerName,
        String customerMobile,
        int playerCount,
        Long pricingPlanId,
        String pricingPlanName,
        Instant startedAt,
        Instant endedAt,
        SessionStatus status,
        Long billId,
        String billNumber,
        PaymentMode paymentMode,
        String cancelReason,
        /** Live estimate for ACTIVE sessions; final figures for COMPLETED ones; null when cancelled. */
        ChargeBreakdown charges
) {
}
