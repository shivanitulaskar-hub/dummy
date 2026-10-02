package com.consoleadda.dto.dashboard;

import com.consoleadda.entity.PaymentMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dashboard figures. {@code today}, stations and rentals are for everyone; profit, month-to-date and charts
 * are admin-only and null for operators (ARCHITECTURE.md A24).
 */
public record DashboardResponse(
        LocalDate businessDate,
        Today today,
        int activeStations,
        int totalStations,
        int rentalsOut,
        int rentalsOverdue,
        Month month,
        List<DayRevenue> revenueByDay,
        List<ModeAmount> paymentMethods,
        List<StationUse> stationUtilization
) {

    public record Today(BigDecimal netSales, BigDecimal gamingRevenue, BigDecimal foodRevenue,
                        BigDecimal rentalRevenue, int billCount, int sessionCount, int gamingMinutes, int foodUnits,
                        BigDecimal averageBill, BigDecimal profitEstimate) {
    }

    public record Month(String month, LocalDate from, LocalDate to, BigDecimal netSales, BigDecimal gamingRevenue,
                        BigDecimal foodRevenue, BigDecimal rentalRevenue, int billCount, int sessionCount,
                        int gamingMinutes, BigDecimal profitEstimate) {
    }

    public record DayRevenue(LocalDate date, BigDecimal gaming, BigDecimal food, BigDecimal rental,
                             BigDecimal netSales) {
    }

    /** Payments received minus refunds. */
    public record ModeAmount(PaymentMode mode, BigDecimal amount) {
    }

    public record StationUse(String stationCode, int sessions, int minutesPlayed, BigDecimal utilizationPercent) {
    }
}
