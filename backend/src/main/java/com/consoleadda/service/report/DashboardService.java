package com.consoleadda.service.report;

import com.consoleadda.dto.dashboard.DashboardResponse;
import com.consoleadda.entity.PaymentMode;
import com.consoleadda.entity.RentalStatus;
import com.consoleadda.entity.SessionStatus;
import com.consoleadda.mapper.RentalMapper;
import com.consoleadda.repository.GameRentalRepository;
import com.consoleadda.repository.GamingSessionRepository;
import com.consoleadda.repository.GamingStationRepository;
import com.consoleadda.service.BusinessDayService;
import com.consoleadda.service.SettingKeys;
import com.consoleadda.service.SettingsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    static final int CHART_DAYS = 30;

    private final SalesQueryService salesQuery;
    private final BusinessDayService businessDay;
    private final GamingStationRepository stationRepository;
    private final GamingSessionRepository sessionRepository;
    private final GameRentalRepository rentalRepository;
    private final RentalMapper rentalMapper;
    private final SettingsService settings;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DashboardService(SalesQueryService salesQuery, BusinessDayService businessDay,
                            GamingStationRepository stationRepository, GamingSessionRepository sessionRepository,
                            GameRentalRepository rentalRepository, RentalMapper rentalMapper, SettingsService settings,
                            JdbcTemplate jdbc, Clock clock) {
        this.salesQuery = salesQuery;
        this.businessDay = businessDay;
        this.stationRepository = stationRepository;
        this.sessionRepository = sessionRepository;
        this.rentalRepository = rentalRepository;
        this.rentalMapper = rentalMapper;
        this.settings = settings;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public DashboardResponse dashboard(boolean admin) {
        LocalDate today = businessDay.today();
        SalesFigures t = salesQuery.forDate(today);
        DashboardResponse.Today todayTiles = new DashboardResponse.Today(t.netSales(), t.gamingRevenue(),
                t.foodRevenue(), t.rentalRevenue(), t.billCount(), t.sessionCount(), t.gamingMinutes(), t.foodUnits(),
                t.averageBill(), admin ? t.profitEstimate() : null);
        int active = sessionRepository.findWithDetailsByStatus(SessionStatus.ACTIVE).size();
        int stations = stationRepository.findByActiveTrueOrderByDisplayOrderAscCodeAsc().size();
        Instant now = clock.instant();
        Duration grace = rentalMapper.grace();
        var out = rentalRepository.findByStatus(RentalStatus.OUT);
        int overdue = (int) out.stream().filter(r -> rentalMapper.isOverdue(r, now, grace)).count();
        if (!admin) {
            return new DashboardResponse(today, todayTiles, active, stations, out.size(), overdue, null, null, null, null);
        }

        LocalDate monthStart = today.withDayOfMonth(1);
        SalesFigures m = salesQuery.total(monthStart, today);
        DashboardResponse.Month month = new DashboardResponse.Month(YearMonth.from(today).toString(), monthStart, today,
                m.netSales(), m.gamingRevenue(), m.foodRevenue(), m.rentalRevenue(), m.billCount(), m.sessionCount(),
                m.gamingMinutes(), m.profitEstimate());

        LocalDate chartFrom = today.minusDays(CHART_DAYS - 1);
        Map<String, SalesFigures> byDay = salesQuery.group(chartFrom, today, SalesQueryService.Grouping.DAY);
        List<DashboardResponse.DayRevenue> days = new ArrayList<>();
        for (LocalDate d = chartFrom; !d.isAfter(today); d = d.plusDays(1)) {
            SalesFigures f = byDay.getOrDefault(d.toString(), SalesFigures.empty());
            days.add(new DashboardResponse.DayRevenue(d, f.gamingRevenue(), f.foodRevenue(), f.rentalRevenue(),
                    f.netSales()));
        }
        List<DashboardResponse.ModeAmount> modes = new ArrayList<>();
        for (PaymentMode mode : PaymentMode.values()) {
            modes.add(new DashboardResponse.ModeAmount(mode, m.payments().get(mode).subtract(m.refunds().get(mode))));
        }
        return new DashboardResponse(today, todayTiles, active, stations, out.size(), overdue, month, days, modes,
                utilization(monthStart, today));
    }

    /** Minutes actually played on paid sessions per active station, against opening hours. */
    private List<DashboardResponse.StationUse> utilization(LocalDate from, LocalDate to) {
        int hoursPerDay = Math.max(1, settings.getInt(SettingKeys.STATION_HOURS_PER_DAY, 12));
        BigDecimal capacity = BigDecimal.valueOf((ChronoUnit.DAYS.between(from, to) + 1) * hoursPerDay * 60L);
        return jdbc.query("""
                SELECT st.code, COUNT(s.id) AS sessions, COALESCE(SUM(s.actual_minutes), 0) AS played
                FROM gaming_stations st
                LEFT JOIN (gaming_sessions s JOIN bills b ON b.id = s.bill_id
                           AND b.status = 'PAID' AND b.business_date BETWEEN ? AND ?) ON s.station_id = st.id
                WHERE st.active
                GROUP BY st.id, st.code, st.display_order ORDER BY st.display_order, st.code""",
                (rs, i) -> new DashboardResponse.StationUse(rs.getString("code"), rs.getInt("sessions"),
                        rs.getInt("played"), ReportService.ratioPercent(BigDecimal.valueOf(rs.getLong("played")), capacity)),
                from, to);
    }
}
