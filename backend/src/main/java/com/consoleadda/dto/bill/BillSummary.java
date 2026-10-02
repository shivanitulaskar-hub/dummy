package com.consoleadda.dto.bill;

import com.consoleadda.entity.BillStatus;
import com.consoleadda.entity.BillType;
import com.consoleadda.entity.PaymentMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BillSummary(Long id, String billNumber, BillType billType, BillStatus status, String customerName,
                          String customerMobile, BigDecimal totalAmount, PaymentMode paymentMode,
                          LocalDate businessDate, Instant paidAt) {
}
