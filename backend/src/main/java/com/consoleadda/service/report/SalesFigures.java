package com.consoleadda.service.report;

import com.consoleadda.entity.PaymentMode;
import com.consoleadda.service.billing.Money;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Sales totals for a business date (or a range). One definition used by daily closing, dashboard and reports:
 * <ul>
 *   <li>Only PAID bills count as sales; a cancelled bill disappears from its day's sales and its refund is
 *   shown on the day it was given.</li>
 *   <li>{@code gaming/food/rentalRevenue}: line amounts before discount and excluding exclusive GST.</li>
 *   <li>{@code netSales}: what the bills actually charged = gross − discounts + exclusive GST + round-off.</li>
 *   <li>Payment, refund and deposit totals come from payment rows dated that business day.</li>
 *   <li>Deposits are not revenue, but cash deposits and their refunds change the cash in the drawer.</li>
 * </ul>
 */
public record SalesFigures(
        int billCount,
        int sessionCount,
        int gamingMinutes,
        int foodUnits,
        int customerCount,
        int rentalCount,
        BigDecimal gamingRevenue,
        BigDecimal foodRevenue,
        BigDecimal rentalRevenue,
        BigDecimal discounts,
        BigDecimal tax,
        BigDecimal roundOff,
        BigDecimal netSales,
        Map<PaymentMode, BigDecimal> payments,
        Map<PaymentMode, BigDecimal> refunds,
        Map<PaymentMode, BigDecimal> deposits,
        Map<PaymentMode, BigDecimal> depositRefunds,
        BigDecimal foodCost
) {

    public BigDecimal grossSales() {
        return gamingRevenue.add(foodRevenue).add(rentalRevenue);
    }

    public BigDecimal totalPayments() {
        return sum(payments);
    }

    public BigDecimal totalRefunds() {
        return sum(refunds);
    }

    public BigDecimal totalDeposits() {
        return sum(deposits);
    }

    public BigDecimal totalDepositRefunds() {
        return sum(depositRefunds);
    }

    /** Sales without GST (what the cafe keeps). */
    public BigDecimal salesExcludingTax() {
        return netSales.subtract(tax);
    }

    /** Estimate only: sales excluding GST minus food cost at current cost prices. */
    public BigDecimal profitEstimate() {
        return salesExcludingTax().subtract(foodCost);
    }

    public BigDecimal averageBill() {
        return billCount == 0 ? Money.ZERO
                : netSales.divide(BigDecimal.valueOf(billCount), Money.SCALE, Money.ROUNDING);
    }

    /** Cash that should be in the drawer: opening float + cash in − cash out (deposits included). */
    public BigDecimal expectedCash(BigDecimal openingCash) {
        return Money.round(openingCash
                .add(payments.get(PaymentMode.CASH)).add(deposits.get(PaymentMode.CASH))
                .subtract(refunds.get(PaymentMode.CASH)).subtract(depositRefunds.get(PaymentMode.CASH)));
    }

    public static Map<PaymentMode, BigDecimal> emptyModes() {
        Map<PaymentMode, BigDecimal> m = new EnumMap<>(PaymentMode.class);
        for (PaymentMode mode : PaymentMode.values()) {
            m.put(mode, Money.ZERO);
        }
        return m;
    }

    public static SalesFigures empty() {
        return new SalesFigures(0, 0, 0, 0, 0, 0, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO,
                Money.ZERO, Money.ZERO, emptyModes(), emptyModes(), emptyModes(), emptyModes(), Money.ZERO);
    }

    private static BigDecimal sum(Map<PaymentMode, BigDecimal> m) {
        return m.values().stream().reduce(Money.ZERO, BigDecimal::add);
    }
}
