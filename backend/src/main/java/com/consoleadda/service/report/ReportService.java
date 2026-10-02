package com.consoleadda.service.report;

import com.consoleadda.dto.game.GameResponse;
import com.consoleadda.dto.inventory.StockRow;
import com.consoleadda.dto.report.ReportColumn;
import com.consoleadda.dto.report.ReportTable;
import com.consoleadda.dto.report.ReportType;
import com.consoleadda.entity.PaymentMode;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.mapper.RentalMapper;
import com.consoleadda.service.GameService;
import com.consoleadda.service.InventoryService;
import com.consoleadda.service.SettingKeys;
import com.consoleadda.service.SettingsService;
import com.consoleadda.service.billing.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.consoleadda.dto.report.ReportColumn.date;
import static com.consoleadda.dto.report.ReportColumn.dateTime;
import static com.consoleadda.dto.report.ReportColumn.decimal;
import static com.consoleadda.dto.report.ReportColumn.integer;
import static com.consoleadda.dto.report.ReportColumn.money;
import static com.consoleadda.dto.report.ReportColumn.percent;
import static com.consoleadda.dto.report.ReportColumn.text;

/**
 * Builds every report as a {@link ReportTable} from bills, payments, sessions and rentals. Dates are business
 * dates (see {@code BusinessDayService}). Sales figures use the same definitions as daily closing
 * ({@link SalesFigures}).
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public static final int MAX_RANGE_DAYS = 731;

    private final SalesQueryService salesQuery;
    private final InventoryService inventoryService;
    private final GameService gameService;
    private final RentalMapper rentalMapper;
    private final SettingsService settings;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ReportService(SalesQueryService salesQuery, InventoryService inventoryService, GameService gameService,
                         RentalMapper rentalMapper, SettingsService settings, JdbcTemplate jdbc, Clock clock) {
        this.salesQuery = salesQuery;
        this.inventoryService = inventoryService;
        this.gameService = gameService;
        this.rentalMapper = rentalMapper;
        this.settings = settings;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public ReportTable build(ReportType type, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw BusinessException.badRequest("INVALID_DATES", "'To' must be on or after 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw BusinessException.badRequest("RANGE_TOO_LONG", "Choose a range of at most 2 years");
        }
        return switch (type) {
            case DAILY -> daily(from, to);
            case MONTHLY -> monthly(from, to);
            case GAMING -> gaming(from, to);
            case FOOD -> food(from, to);
            case PAYMENTS -> payments(from, to);
            case STATIONS -> stations(from, to);
            case CUSTOMERS -> customers(from, to);
            case RENTALS -> rentals(from, to);
            case GAME_ROI -> gameRoi(from, to);
            case INVENTORY -> inventory(from, to);
        };
    }

    // ---------------------------------------------------------------- sales

    private static final List<ReportColumn> SALES_COLUMNS = List.of(
            integer("bills", "Bills"), integer("sessions", "Sessions"), decimal("gamingHours", "Gaming hours"),
            integer("foodUnits", "Food units"), money("gaming", "Gaming"), money("food", "Food"),
            money("rental", "Rentals"), money("discounts", "Discounts"), money("gst", "GST"),
            money("netSales", "Net sales"), money("cash", "Cash"), money("upi", "UPI"), money("card", "Card"),
            money("other", "Other"), money("refunds", "Refunds"));

    private static final String SALES_NOTE = "Paid bills only. Gaming/Food/Rentals are amounts before discount; "
            + "Net sales = what the bills charged (after discount, with GST and round-off). Cash/UPI/Card/Other "
            + "are payments received on the day; refunds are listed on the day they were given.";

    private ReportTable daily(LocalDate from, LocalDate to) {
        Map<String, SalesFigures> byDay = salesQuery.group(from, to, SalesQueryService.Grouping.DAY);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d);
            putSales(row, byDay.getOrDefault(d.toString(), SalesFigures.empty()));
            rows.add(row);
        }
        List<ReportColumn> columns = new ArrayList<>();
        columns.add(date("date", "Date"));
        columns.addAll(SALES_COLUMNS);
        Map<String, Object> totals = new LinkedHashMap<>();
        putSales(totals, salesQuery.total(from, to));
        return table(ReportType.DAILY, from, to, columns, rows, totals, SALES_NOTE);
    }

    private ReportTable monthly(LocalDate from, LocalDate to) {
        LocalDate start = from.withDayOfMonth(1);
        LocalDate end = YearMonth.from(to).atEndOfMonth();
        Map<String, SalesFigures> byMonth = salesQuery.group(start, end, SalesQueryService.Grouping.MONTH);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (YearMonth m = YearMonth.from(start); !m.isAfter(YearMonth.from(end)); m = m.plusMonths(1)) {
            SalesFigures f = byMonth.getOrDefault(m.toString(), SalesFigures.empty());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", m.toString());
            putSales(row, f);
            row.put("customers", f.customerCount());
            row.put("averageBill", f.averageBill());
            rows.add(row);
        }
        List<ReportColumn> columns = new ArrayList<>();
        columns.add(text("month", "Month"));
        columns.addAll(SALES_COLUMNS);
        columns.add(integer("customers", "Customers"));
        columns.add(money("averageBill", "Avg bill"));
        SalesFigures all = salesQuery.total(start, end);
        Map<String, Object> totals = new LinkedHashMap<>();
        putSales(totals, all);
        totals.put("customers", all.customerCount());
        totals.put("averageBill", all.averageBill());
        return table(ReportType.MONTHLY, start, end, columns, rows, totals,
                SALES_NOTE + " Whole calendar months are shown.");
    }

    private static void putSales(Map<String, Object> row, SalesFigures f) {
        row.put("bills", f.billCount());
        row.put("sessions", f.sessionCount());
        row.put("gamingHours", hours(f.gamingMinutes()));
        row.put("foodUnits", f.foodUnits());
        row.put("gaming", f.gamingRevenue());
        row.put("food", f.foodRevenue());
        row.put("rental", f.rentalRevenue());
        row.put("discounts", f.discounts());
        row.put("gst", f.tax());
        row.put("netSales", f.netSales());
        row.put("cash", f.payments().get(PaymentMode.CASH));
        row.put("upi", f.payments().get(PaymentMode.UPI));
        row.put("card", f.payments().get(PaymentMode.CARD));
        row.put("other", f.payments().get(PaymentMode.OTHER));
        row.put("refunds", f.totalRefunds());
    }

    // ---------------------------------------------------------------- gaming

    private ReportTable gaming(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT b.business_date, b.bill_number, st.code, b.customer_name, b.customer_mobile, s.player_count,
                       s.started_at, s.ended_at, s.actual_minutes, s.credit_minutes, s.billable_minutes, s.gaming_amount
                FROM gaming_sessions s JOIN bills b ON b.id = s.bill_id JOIN gaming_stations st ON st.id = s.station_id
                WHERE b.status = 'PAID' AND b.business_date BETWEEN ? AND ?
                ORDER BY s.started_at""", (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", rs.getObject("business_date", LocalDate.class));
            row.put("billNumber", rs.getString("bill_number"));
            row.put("station", rs.getString("code"));
            row.put("customer", rs.getString("customer_name"));
            row.put("mobile", rs.getString("customer_mobile"));
            row.put("players", rs.getInt("player_count"));
            row.put("start", instant(rs.getTimestamp("started_at")));
            row.put("end", instant(rs.getTimestamp("ended_at")));
            row.put("playedMinutes", rs.getInt("actual_minutes"));
            row.put("creditMinutes", rs.getInt("credit_minutes"));
            row.put("billedMinutes", rs.getInt("billable_minutes"));
            row.put("amount", Money.nz(rs.getBigDecimal("gaming_amount")));
            return row;
        }, from, to);
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("billNumber", rows.size() + " sessions");
        totals.put("playedMinutes", sumInt(rows, "playedMinutes"));
        totals.put("creditMinutes", sumInt(rows, "creditMinutes"));
        totals.put("billedMinutes", sumInt(rows, "billedMinutes"));
        totals.put("amount", sumMoney(rows, "amount"));
        return table(ReportType.GAMING, from, to, List.of(date("date", "Date"), text("billNumber", "Bill"),
                        text("station", "Station"), text("customer", "Customer"), text("mobile", "Mobile"),
                        integer("players", "Players"), dateTime("start", "Start"), dateTime("end", "End"),
                        integer("playedMinutes", "Played (min)"), integer("creditMinutes", "Free credit (min)"),
                        integer("billedMinutes", "Billed (min)"), money("amount", "Gaming charge")),
                rows, totals, "Completed sessions on paid bills. Gaming charge is before any bill discount and GST.");
    }

    // ---------------------------------------------------------------- food

    private ReportTable food(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT COALESCE(f.name, bi.description) AS item, COALESCE(c.name, '') AS category,
                       SUM(bi.quantity) AS qty, SUM(bi.line_total) AS sales,
                       SUM(bi.quantity * COALESCE(f.cost_price, 0)) AS cost
                FROM bill_items bi JOIN bills b ON b.id = bi.bill_id
                LEFT JOIN food_items f ON f.id = bi.food_item_id
                LEFT JOIN food_categories c ON c.id = f.category_id
                WHERE bi.item_type = 'FOOD' AND b.status = 'PAID' AND b.business_date BETWEEN ? AND ?
                GROUP BY COALESCE(f.name, bi.description), COALESCE(c.name, '')
                ORDER BY sales DESC, item""", (rs, i) -> {
            BigDecimal sales = Money.nz(rs.getBigDecimal("sales"));
            BigDecimal cost = Money.round(Money.nz(rs.getBigDecimal("cost")));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item", rs.getString("item"));
            row.put("category", rs.getString("category"));
            row.put("quantity", rs.getLong("qty"));
            row.put("sales", sales);
            row.put("cost", cost);
            row.put("margin", sales.subtract(cost));
            return row;
        }, from, to);
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("quantity", rows.stream().mapToLong(r -> (Long) r.get("quantity")).sum());
        totals.put("sales", sumMoney(rows, "sales"));
        totals.put("cost", sumMoney(rows, "cost"));
        totals.put("margin", sumMoney(rows, "margin"));
        return table(ReportType.FOOD, from, to, List.of(text("item", "Item"), text("category", "Category"),
                        integer("quantity", "Qty sold"), money("sales", "Sales"), money("cost", "Cost (est.)"),
                        money("margin", "Margin (est.)")), rows, totals,
                "Food sold on paid bills, before bill discounts and exclusive GST. Cost uses each item's current "
                        + "cost price, so it is an estimate until cost prices are kept up to date.");
    }

    // ---------------------------------------------------------------- payments

    private ReportTable payments(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT p.business_date, p.mode,
                       SUM(p.payment_type = 'PAYMENT') AS cnt,
                       SUM(CASE WHEN p.payment_type = 'PAYMENT' THEN p.amount ELSE 0 END) AS received,
                       SUM(CASE WHEN p.payment_type = 'REFUND' THEN p.amount ELSE 0 END) AS refunds,
                       SUM(CASE WHEN p.payment_type = 'DEPOSIT' THEN p.amount ELSE 0 END) AS deposits,
                       SUM(CASE WHEN p.payment_type = 'DEPOSIT_REFUND' THEN p.amount ELSE 0 END) AS deposit_refunds
                FROM payments p WHERE p.business_date BETWEEN ? AND ?
                GROUP BY p.business_date, p.mode ORDER BY p.business_date, p.mode""", (rs, i) -> {
            BigDecimal received = Money.nz(rs.getBigDecimal("received"));
            BigDecimal refunds = Money.nz(rs.getBigDecimal("refunds"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", rs.getObject("business_date", LocalDate.class));
            row.put("mode", rs.getString("mode"));
            row.put("count", rs.getInt("cnt"));
            row.put("received", received);
            row.put("refunds", refunds);
            row.put("net", received.subtract(refunds));
            row.put("deposits", Money.nz(rs.getBigDecimal("deposits")));
            row.put("depositRefunds", Money.nz(rs.getBigDecimal("deposit_refunds")));
            return row;
        }, from, to);
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("count", sumInt(rows, "count"));
        for (String k : List.of("received", "refunds", "net", "deposits", "depositRefunds")) {
            totals.put(k, sumMoney(rows, k));
        }
        return table(ReportType.PAYMENTS, from, to, List.of(date("date", "Date"), text("mode", "Mode"),
                        integer("count", "Payments"), money("received", "Received"), money("refunds", "Refunds"),
                        money("net", "Net"), money("deposits", "Deposits taken"),
                        money("depositRefunds", "Deposits returned")), rows, totals,
                "Money in and out per business day and payment mode. Rental deposits are shown separately: they "
                        + "are held for the customer, not revenue.");
    }

    // ---------------------------------------------------------------- stations

    private ReportTable stations(LocalDate from, LocalDate to) {
        int hoursPerDay = Math.max(1, settings.getInt(SettingKeys.STATION_HOURS_PER_DAY, 12));
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        BigDecimal capacity = BigDecimal.valueOf(days * hoursPerDay * 60L);
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT st.code, st.name, st.active, COUNT(s.id) AS sessions,
                       COALESCE(SUM(s.actual_minutes), 0) AS played, COALESCE(SUM(s.billable_minutes), 0) AS billed,
                       COALESCE(SUM(s.gaming_amount), 0) AS revenue
                FROM gaming_stations st
                LEFT JOIN (gaming_sessions s JOIN bills b ON b.id = s.bill_id
                           AND b.status = 'PAID' AND b.business_date BETWEEN ? AND ?) ON s.station_id = st.id
                GROUP BY st.id, st.code, st.name, st.active, st.display_order
                HAVING st.active OR COUNT(s.id) > 0
                ORDER BY st.display_order, st.code""", (rs, i) -> {
            long played = rs.getLong("played");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("station", rs.getString("code"));
            row.put("name", rs.getString("name"));
            row.put("sessions", rs.getInt("sessions"));
            row.put("playedHours", hours(played));
            row.put("billedHours", hours(rs.getLong("billed")));
            row.put("revenue", Money.nz(rs.getBigDecimal("revenue")));
            row.put("utilization", ratioPercent(BigDecimal.valueOf(played), capacity));
            return row;
        }, from, to);
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("sessions", sumInt(rows, "sessions"));
        totals.put("playedHours", sumDecimal(rows, "playedHours"));
        totals.put("billedHours", sumDecimal(rows, "billedHours"));
        totals.put("revenue", sumMoney(rows, "revenue"));
        return table(ReportType.STATIONS, from, to, List.of(text("station", "Station"), text("name", "Name"),
                        integer("sessions", "Sessions"), decimal("playedHours", "Hours played"),
                        decimal("billedHours", "Hours billed"), money("revenue", "Gaming revenue"),
                        percent("utilization", "Utilization %")), rows, totals,
                "Utilization = hours played ÷ (" + days + " day(s) × " + hoursPerDay + " opening hours). Change "
                        + "opening hours in the setting '" + SettingKeys.STATION_HOURS_PER_DAY + "'.");
    }

    // ---------------------------------------------------------------- customers

    private ReportTable customers(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT c.name, c.mobile, COUNT(b.id) AS bills, SUM(b.total_amount) AS spent, MAX(b.paid_at) AS last_at,
                       COALESCE(MAX(gs.sessions), 0) AS sessions, COALESCE(MAX(gs.minutes), 0) AS minutes,
                       c.total_spent
                FROM bills b JOIN customers c ON c.id = b.customer_id
                LEFT JOIN (SELECT s.customer_id, COUNT(*) AS sessions, SUM(s.billable_minutes) AS minutes
                           FROM gaming_sessions s JOIN bills sb ON sb.id = s.bill_id
                           WHERE sb.status = 'PAID' AND sb.business_date BETWEEN ? AND ?
                           GROUP BY s.customer_id) gs ON gs.customer_id = c.id
                WHERE b.status = 'PAID' AND b.business_date BETWEEN ? AND ?
                GROUP BY c.id, c.name, c.mobile, c.total_spent
                ORDER BY spent DESC, c.name""", (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", rs.getString("name"));
            row.put("mobile", rs.getString("mobile"));
            row.put("bills", rs.getInt("bills"));
            row.put("sessions", rs.getInt("sessions"));
            row.put("gamingHours", hours(rs.getLong("minutes")));
            row.put("spent", Money.nz(rs.getBigDecimal("spent")));
            row.put("lastVisit", instant(rs.getTimestamp("last_at")));
            row.put("lifetimeSpent", Money.nz(rs.getBigDecimal("total_spent")));
            return row;
        }, from, to, from, to);
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("name", rows.size() + " customers");
        totals.put("bills", sumInt(rows, "bills"));
        totals.put("sessions", sumInt(rows, "sessions"));
        totals.put("gamingHours", sumDecimal(rows, "gamingHours"));
        totals.put("spent", sumMoney(rows, "spent"));
        return table(ReportType.CUSTOMERS, from, to, List.of(text("name", "Customer"), text("mobile", "Mobile"),
                        integer("bills", "Bills"), integer("sessions", "Sessions"), decimal("gamingHours", "Gaming hours"),
                        money("spent", "Spent in period"), dateTime("lastVisit", "Last bill in period"),
                        money("lifetimeSpent", "Lifetime spend")), rows, totals,
                "Customers identified by mobile number on paid bills, highest spend first. Walk-ins without a "
                        + "mobile number are not listed.");
    }

    // ---------------------------------------------------------------- rentals

    private ReportTable rentals(LocalDate from, LocalDate to) {
        Instant now = clock.instant();
        Duration grace = rentalMapper.grace();
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT r.rental_number, g.name, g.copy_number, r.customer_name, r.customer_mobile, r.out_at,
                       r.expected_return_at, r.returned_at, r.status, r.rental_days, r.rental_price, r.late_days,
                       r.late_fee, r.deposit_amount, r.deposit_refunded
                FROM game_rentals r JOIN game_master g ON g.id = r.game_id JOIN bills b ON b.id = r.bill_id
                WHERE b.business_date BETWEEN ? AND ? ORDER BY r.out_at""", (rs, i) -> {
            Instant due = instant(rs.getTimestamp("expected_return_at"));
            String status = rs.getString("status");
            if ("OUT".equals(status) && now.isAfter(due.plus(grace))) {
                status = "OVERDUE";
            }
            int copy = rs.getInt("copy_number");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rentalNumber", rs.getString("rental_number"));
            row.put("game", copy > 1 ? rs.getString("name") + " (copy " + copy + ")" : rs.getString("name"));
            row.put("customer", rs.getString("customer_name"));
            row.put("mobile", rs.getString("customer_mobile"));
            row.put("out", instant(rs.getTimestamp("out_at")));
            row.put("due", due);
            row.put("returned", instant(rs.getTimestamp("returned_at")));
            row.put("status", status);
            row.put("days", rs.getInt("rental_days"));
            row.put("rentalPrice", Money.nz(rs.getBigDecimal("rental_price")));
            row.put("lateDays", rs.getInt("late_days"));
            row.put("lateFee", Money.nz(rs.getBigDecimal("late_fee")));
            row.put("deposit", Money.nz(rs.getBigDecimal("deposit_amount")));
            row.put("depositReturned", Money.nz(rs.getBigDecimal("deposit_refunded")));
            return row;
        }, from, to);
        List<Map<String, Object>> counted = rows.stream().filter(r -> !"CANCELLED".equals(r.get("status"))).toList();
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("rentalNumber", counted.size() + " rentals");
        totals.put("rentalPrice", sumMoney(counted, "rentalPrice"));
        totals.put("lateDays", sumInt(counted, "lateDays"));
        totals.put("lateFee", sumMoney(counted, "lateFee"));
        return table(ReportType.RENTALS, from, to, List.of(text("rentalNumber", "Rental"), text("game", "Game"),
                        text("customer", "Customer"), text("mobile", "Mobile"), dateTime("out", "Out"),
                        dateTime("due", "Due"), dateTime("returned", "Returned"), text("status", "Status"),
                        integer("days", "Days"), money("rentalPrice", "Rental"), integer("lateDays", "Late days"),
                        money("lateFee", "Late fee"), money("deposit", "Deposit"),
                        money("depositReturned", "Deposit returned")), rows, totals,
                "Rentals made in the period (by rental date). Totals exclude cancelled rentals. Amounts are before GST.");
    }

    private ReportTable gameRoi(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GameResponse g : gameService.list(true, true)) {
            GameResponse.Roi roi = g.roi();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("game", g.name());
            row.put("copy", g.copyNumber());
            row.put("status", !g.active() ? "Inactive" : g.currentRental() == null ? "Available"
                    : g.currentRental().overdue() ? "Overdue" : "Rented out");
            row.put("purchaseCost", roi.purchaseCost());
            row.put("rentalPrice", g.rentalPrice());
            row.put("rentals", roi.rentals());
            row.put("revenue", roi.revenue());
            row.put("remaining", roi.remaining());
            row.put("roi", roi.roiPercent());
            row.put("breakEven", roi.breakEvenRentals());
            row.put("toRecover", roi.rentalsToRecover());
            rows.add(row);
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("game", rows.size() + " discs");
        totals.put("purchaseCost", sumMoney(rows, "purchaseCost"));
        totals.put("rentals", rows.stream().mapToLong(r -> (Long) r.get("rentals")).sum());
        totals.put("revenue", sumMoney(rows, "revenue"));
        totals.put("remaining", sumMoney(rows, "remaining"));
        BigDecimal cost = (BigDecimal) totals.get("purchaseCost");
        totals.put("roi", cost.signum() == 0 ? null
                : ratioPercent(((BigDecimal) totals.get("revenue")).subtract(cost), cost));
        return table(ReportType.GAME_ROI, from, to, List.of(text("game", "Game"), integer("copy", "Copy"),
                        text("status", "Status"), money("purchaseCost", "Purchase cost"),
                        money("rentalPrice", "Rental price"), integer("rentals", "Rentals"), money("revenue", "Revenue"),
                        money("remaining", "Left to recover"), percent("roi", "ROI %"),
                        integer("breakEven", "Rentals to break even"), integer("toRecover", "Rentals still needed")),
                rows, totals, "All-time figures per disc (the date range is not used). Revenue = rental charges + "
                        + "late fees of rentals that were not cancelled, before GST. ROI = (revenue − cost) ÷ cost.");
    }

    // ---------------------------------------------------------------- inventory

    private ReportTable inventory(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StockRow s : inventoryService.stock(from, to)) {
            if (!s.trackInventory()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item", s.name());
            row.put("category", s.category());
            row.put("opening", s.opening());
            row.put("purchases", s.purchases());
            row.put("sales", s.sales());
            row.put("wastage", s.wastage());
            row.put("adjustments", s.adjustments());
            row.put("closing", s.closing());
            row.put("current", s.currentStock());
            row.put("reorderLevel", s.reorderLevel());
            row.put("low", s.lowStock() ? "LOW" : "");
            rows.add(row);
        }
        return table(ReportType.INVENTORY, from, to, List.of(text("item", "Item"), text("category", "Category"),
                        integer("opening", "Opening"), integer("purchases", "Purchases"), integer("sales", "Sales"),
                        integer("wastage", "Wastage"), integer("adjustments", "Adjustments"),
                        integer("closing", "Closing"), integer("current", "Stock now"),
                        integer("reorderLevel", "Reorder level"), text("low", "Low stock")), rows, null,
                "Closing = Opening + Purchases − Sales − Wastage ± Adjustments, for items that track stock.");
    }

    // ---------------------------------------------------------------- helpers

    private static ReportTable table(ReportType type, LocalDate from, LocalDate to, List<ReportColumn> columns,
                                     List<Map<String, Object>> rows, Map<String, Object> totals, String note) {
        return new ReportTable(type.slug(), type.title(), from, to, columns, rows, totals, note);
    }

    private static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal ratioPercent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : part.multiply(Money.HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static int sumInt(List<Map<String, Object>> rows, String key) {
        return rows.stream().mapToInt(r -> ((Number) r.get(key)).intValue()).sum();
    }

    private static BigDecimal sumMoney(List<Map<String, Object>> rows, String key) {
        return rows.stream().map(r -> (BigDecimal) r.get(key)).reduce(Money.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumDecimal(List<Map<String, Object>> rows, String key) {
        return rows.stream().map(r -> (BigDecimal) r.get(key)).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
