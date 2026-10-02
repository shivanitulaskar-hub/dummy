package com.consoleadda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** End-of-day snapshot. Figures are frozen when the day is closed; reopening keeps them for the record. */
@Getter
@Setter
@Entity
@Table(name = "daily_closings")
public class DailyClosing extends BaseEntity {

    @Column(name = "business_date", nullable = false, unique = true)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DayStatus status = DayStatus.OPEN;

    @Column(name = "gaming_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal gamingRevenue = BigDecimal.ZERO;

    @Column(name = "food_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal foodRevenue = BigDecimal.ZERO;

    @Column(name = "rental_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentalRevenue = BigDecimal.ZERO;

    @Column(name = "total_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "cash_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal cashTotal = BigDecimal.ZERO;

    @Column(name = "upi_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal upiTotal = BigDecimal.ZERO;

    @Column(name = "card_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal cardTotal = BigDecimal.ZERO;

    @Column(name = "other_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal otherTotal = BigDecimal.ZERO;

    @Column(name = "session_count", nullable = false)
    private int sessionCount;

    @Column(name = "bill_count", nullable = false)
    private int billCount;

    @Column(name = "customer_count", nullable = false)
    private int customerCount;

    @Column(name = "gaming_minutes", nullable = false)
    private int gamingMinutes;

    @Column(name = "food_units_sold", nullable = false)
    private int foodUnitsSold;

    @Column(name = "discount_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "round_off_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal roundOffTotal = BigDecimal.ZERO;

    @Column(name = "refund_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundTotal = BigDecimal.ZERO;

    @Column(name = "deposit_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositTotal = BigDecimal.ZERO;

    @Column(name = "deposit_refund_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositRefundTotal = BigDecimal.ZERO;

    @Column(name = "opening_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingCash = BigDecimal.ZERO;

    @Column(name = "expected_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedCash = BigDecimal.ZERO;

    @Column(name = "actual_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal actualCash = BigDecimal.ZERO;

    @Column(name = "cash_difference", nullable = false, precision = 12, scale = 2)
    private BigDecimal cashDifference = BigDecimal.ZERO;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "reopened_by")
    private Long reopenedBy;

    @Column(name = "reopened_at")
    private Instant reopenedAt;

    @Column(name = "reopen_reason", length = 255)
    private String reopenReason;
}
