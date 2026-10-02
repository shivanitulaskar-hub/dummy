package com.consoleadda.dto.bill;

import com.consoleadda.entity.BillStatus;
import com.consoleadda.entity.BillType;
import com.consoleadda.entity.PaymentMode;
import com.consoleadda.entity.PaymentType;
import com.consoleadda.service.billing.PriceMode;
import com.consoleadda.service.billing.TaxCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BillDetail(
        Long id,
        String billNumber,
        BillType billType,
        BillStatus status,
        String customerName,
        String customerMobile,
        LocalDate businessDate,
        Instant createdAt,
        Instant paidAt,
        Gaming gaming,
        List<Line> lines,
        BigDecimal gamingAmount,
        BigDecimal foodAmount,
        BigDecimal rentalAmount,
        BigDecimal subtotal,
        String discountName,
        BigDecimal discountAmount,
        BigDecimal taxableAmount,
        BigDecimal taxAmount,
        List<Tax> taxLines,
        BigDecimal roundOff,
        BigDecimal totalAmount,
        List<PaymentLine> payments,
        List<Credit> credits,
        Instant cancelledAt,
        String cancelReason
) {

    public record Gaming(Long sessionId, String stationCode, int playerCount, Instant startedAt, Instant endedAt,
                         Integer actualMinutes, int creditMinutes, Integer billableMinutes, String pricingPlan) {
    }

    public record Line(Long id, String type, String description, int quantity, BigDecimal unitPrice, BigDecimal amount) {
    }

    /** CGST/SGST are each half of the GST amount (intra-state supply). */
    public record Tax(TaxCategory category, String hsnSacCode, BigDecimal taxableValue, BigDecimal ratePercent,
                      PriceMode priceMode, BigDecimal taxAmount, BigDecimal cgst, BigDecimal sgst) {
    }

    public record PaymentLine(Long id, PaymentType type, PaymentMode mode, BigDecimal amount, String referenceNo,
                              String reason, Instant receivedAt) {
    }

    public record Credit(int minutes, String reason) {
    }
}
