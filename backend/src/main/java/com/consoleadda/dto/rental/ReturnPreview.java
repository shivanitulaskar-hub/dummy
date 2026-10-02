package com.consoleadda.dto.rental;

import com.consoleadda.entity.PaymentMode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What happens if the disc is returned now: late fee (and its bill total including any GST) to collect, and
 * the deposit to give back in the mode it was taken.
 */
public record ReturnPreview(Long rentalId, Instant expectedReturnAt, Instant returnAt, int lateDays,
                            BigDecimal lateFeePerDay, BigDecimal lateFee, BigDecimal lateFeeTotal,
                            BigDecimal depositToRefund, PaymentMode depositMode) {
}
