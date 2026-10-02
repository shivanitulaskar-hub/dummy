package com.consoleadda.service.report;

import com.consoleadda.entity.PaymentMode;
import com.consoleadda.service.billing.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregates {@link SalesFigures} straight from bills and payments with SQL, grouped by business date, by
 * month, or over the whole range. Read-only; never cached, so figures are always current.
 */
@Service
@Transactional(readOnly = true)
public class SalesQueryService {

    /** How rows are grouped. The value is a SQL expression over a {@code business_date} column. */
    public enum Grouping {
        DAY("DATE_FORMAT(%s, '%%Y-%%m-%%d')"),
        MONTH("DATE_FORMAT(%s, '%%Y-%%m')"),
        TOTAL("'TOTAL'");

        private final String template;

        Grouping(String template) {
            this.template = template;
        }

        String on(String column) {
            return String.format(template, column);
        }
    }

    private final JdbcTemplate jdbc;

    public SalesQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SalesFigures total(LocalDate from, LocalDate to) {
        return group(from, to, Grouping.TOTAL).getOrDefault("TOTAL", SalesFigures.empty());
    }

    public SalesFigures forDate(LocalDate date) {
        return total(date, date);
    }

    /** Figures per group key ("2026-10-02", "2026-10" or "TOTAL"); groups without any activity are absent. */
    public Map<String, SalesFigures> group(LocalDate from, LocalDate to, Grouping grouping) {
        Map<String, Builder> rows = new TreeMap<>();
        String b = grouping.on("b.business_date");

        jdbc.query("SELECT " + b + " AS k, COUNT(*) AS bills, SUM(b.gaming_amount) AS gaming, "
                        + "SUM(b.food_amount) AS food, SUM(b.rental_amount) AS rental, SUM(b.discount_amount) AS discount, "
                        + "SUM(b.tax_amount) AS tax, SUM(b.round_off) AS round_off, SUM(b.total_amount) AS total, "
                        + "COUNT(DISTINCT b.customer_id) + SUM(b.customer_id IS NULL) AS customers "
                        + "FROM bills b WHERE b.status = 'PAID' AND b.business_date BETWEEN ? AND ? GROUP BY k",
                rs -> {
                    Builder r = rows.computeIfAbsent(rs.getString("k"), k -> new Builder());
                    r.bills = rs.getInt("bills");
                    r.gaming = money(rs.getBigDecimal("gaming"));
                    r.food = money(rs.getBigDecimal("food"));
                    r.rental = money(rs.getBigDecimal("rental"));
                    r.discount = money(rs.getBigDecimal("discount"));
                    r.tax = money(rs.getBigDecimal("tax"));
                    r.roundOff = money(rs.getBigDecimal("round_off"));
                    r.net = money(rs.getBigDecimal("total"));
                    r.customers = rs.getInt("customers");
                }, from, to);

        jdbc.query("SELECT " + b + " AS k, COUNT(*) AS sessions, COALESCE(SUM(s.billable_minutes), 0) AS minutes "
                        + "FROM gaming_sessions s JOIN bills b ON b.id = s.bill_id "
                        + "WHERE b.status = 'PAID' AND b.business_date BETWEEN ? AND ? GROUP BY k",
                rs -> {
                    Builder r = rows.computeIfAbsent(rs.getString("k"), k -> new Builder());
                    r.sessions = rs.getInt("sessions");
                    r.minutes = rs.getInt("minutes");
                }, from, to);

        jdbc.query("SELECT " + b + " AS k, SUM(bi.quantity) AS units, "
                        + "COALESCE(SUM(bi.quantity * f.cost_price), 0) AS cost "
                        + "FROM bill_items bi JOIN bills b ON b.id = bi.bill_id "
                        + "LEFT JOIN food_items f ON f.id = bi.food_item_id "
                        + "WHERE bi.item_type = 'FOOD' AND b.status = 'PAID' AND b.business_date BETWEEN ? AND ? "
                        + "GROUP BY k",
                rs -> {
                    Builder r = rows.computeIfAbsent(rs.getString("k"), k -> new Builder());
                    r.foodUnits = rs.getInt("units");
                    r.foodCost = money(rs.getBigDecimal("cost"));
                }, from, to);

        jdbc.query("SELECT " + b + " AS k, COUNT(*) AS rentals FROM game_rentals r JOIN bills b ON b.id = r.bill_id "
                        + "WHERE b.status = 'PAID' AND b.business_date BETWEEN ? AND ? GROUP BY k",
                rs -> {
                    rows.computeIfAbsent(rs.getString("k"), k -> new Builder()).rentals = rs.getInt("rentals");
                }, from, to);

        jdbc.query("SELECT " + grouping.on("p.business_date") + " AS k, p.payment_type, p.mode, SUM(p.amount) AS amount "
                        + "FROM payments p WHERE p.business_date BETWEEN ? AND ? GROUP BY k, p.payment_type, p.mode",
                rs -> {
                    Builder r = rows.computeIfAbsent(rs.getString("k"), k -> new Builder());
                    PaymentMode mode = PaymentMode.valueOf(rs.getString("mode"));
                    BigDecimal amount = money(rs.getBigDecimal("amount"));
                    Map<PaymentMode, BigDecimal> target = switch (rs.getString("payment_type")) {
                        case "PAYMENT" -> r.payments;
                        case "REFUND" -> r.refunds;
                        case "DEPOSIT" -> r.deposits;
                        case "DEPOSIT_REFUND" -> r.depositRefunds;
                        default -> throw new IllegalStateException("Unknown payment type");
                    };
                    target.merge(mode, amount, BigDecimal::add);
                }, from, to);

        Map<String, SalesFigures> result = new TreeMap<>();
        rows.forEach((k, r) -> result.put(k, r.build()));
        return result;
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? Money.ZERO : Money.round(v);
    }

    private static final class Builder {
        int bills;
        int sessions;
        int minutes;
        int foodUnits;
        int customers;
        int rentals;
        BigDecimal gaming = Money.ZERO;
        BigDecimal food = Money.ZERO;
        BigDecimal rental = Money.ZERO;
        BigDecimal discount = Money.ZERO;
        BigDecimal tax = Money.ZERO;
        BigDecimal roundOff = Money.ZERO;
        BigDecimal net = Money.ZERO;
        BigDecimal foodCost = Money.ZERO;
        final Map<PaymentMode, BigDecimal> payments = SalesFigures.emptyModes();
        final Map<PaymentMode, BigDecimal> refunds = SalesFigures.emptyModes();
        final Map<PaymentMode, BigDecimal> deposits = SalesFigures.emptyModes();
        final Map<PaymentMode, BigDecimal> depositRefunds = SalesFigures.emptyModes();

        SalesFigures build() {
            return new SalesFigures(bills, sessions, minutes, foodUnits, customers, rentals, gaming, food, rental,
                    discount, tax, roundOff, net, payments, refunds, deposits, depositRefunds, foodCost);
        }
    }
}
