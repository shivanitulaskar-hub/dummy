package com.consoleadda.dto.closing;

import com.consoleadda.entity.DayStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A business day for the closing screen. {@code live} = figures calculated now (day not closed);
 * otherwise they are the snapshot frozen at closing.
 */
public record DailyClosingResponse(
        LocalDate businessDate,
        DayStatus status,
        boolean live,
        ClosingFigures figures,
        CashBreakdown cash,
        BigDecimal actualCash,
        BigDecimal cashDifference,
        String notes,
        List<String> activeSessions,
        String closedBy,
        Instant closedAt,
        String reopenedBy,
        Instant reopenedAt,
        String reopenReason
) {
}
