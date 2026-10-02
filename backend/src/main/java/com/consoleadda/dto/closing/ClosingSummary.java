package com.consoleadda.dto.closing;

import com.consoleadda.entity.DayStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ClosingSummary(LocalDate businessDate, DayStatus status, BigDecimal totalRevenue,
                             BigDecimal expectedCash, BigDecimal actualCash, BigDecimal cashDifference,
                             String closedBy, Instant closedAt) {
}
