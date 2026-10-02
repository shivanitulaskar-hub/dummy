package com.consoleadda.dto.rental;

import com.consoleadda.entity.PaymentMode;
import com.consoleadda.entity.RentalStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A rental. {@code status} is OVERDUE while the disc is out past its expected return time plus the grace
 * period. {@code accruedLateDays/Fee} show what the late fee would be if the disc came back now.
 */
public record RentalResponse(
        Long id,
        String rentalNumber,
        Long gameId,
        String gameName,
        int copyNumber,
        Long customerId,
        String customerName,
        String customerMobile,
        int rentalDays,
        BigDecimal rentalPrice,
        BigDecimal depositAmount,
        Instant outAt,
        Instant expectedReturnAt,
        Instant returnedAt,
        RentalStatus status,
        int lateDays,
        BigDecimal lateFee,
        int accruedLateDays,
        BigDecimal accruedLateFee,
        BigDecimal totalAmount,
        BigDecimal depositRefunded,
        PaymentMode paymentMode,
        Long billId,
        String billNumber,
        Long lateFeeBillId,
        String lateFeeBillNumber,
        String notes
) {
}
