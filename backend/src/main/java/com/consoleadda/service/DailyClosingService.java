package com.consoleadda.service;

import com.consoleadda.dto.closing.CashBreakdown;
import com.consoleadda.dto.closing.CloseDayRequest;
import com.consoleadda.dto.closing.ClosingFigures;
import com.consoleadda.dto.closing.ClosingSummary;
import com.consoleadda.dto.closing.DailyClosingResponse;
import com.consoleadda.dto.closing.ReopenDayRequest;
import com.consoleadda.entity.DailyClosing;
import com.consoleadda.entity.DayStatus;
import com.consoleadda.entity.GamingSession;
import com.consoleadda.entity.PaymentMode;
import com.consoleadda.entity.SessionStatus;
import com.consoleadda.entity.User;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.repository.DailyClosingRepository;
import com.consoleadda.repository.GamingSessionRepository;
import com.consoleadda.repository.UserRepository;
import com.consoleadda.security.AuthenticatedUser;
import com.consoleadda.security.CurrentUser;
import com.consoleadda.service.billing.Money;
import com.consoleadda.service.report.SalesFigures;
import com.consoleadda.service.report.SalesQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * End-of-day closing. The operator reviews the day's totals, counts the cash and closes the day; the figures
 * are frozen and the day is locked (no payments, cancellations or stock entries) until an admin reopens it.
 * <p>
 * Locking: closing takes an exclusive lock on the day's row; everything that moves money on that day takes a
 * shared lock first ({@link BusinessDayService#ensureOpen}). See ARCHITECTURE.md §2.1.
 */
@Slf4j
@Service
public class DailyClosingService {

    private final DailyClosingRepository repository;
    private final GamingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SalesQueryService salesQuery;
    private final BusinessDayService businessDay;
    private final AuditService auditService;
    private final Clock clock;

    public DailyClosingService(DailyClosingRepository repository, GamingSessionRepository sessionRepository,
                               UserRepository userRepository, SalesQueryService salesQuery,
                               BusinessDayService businessDay, AuditService auditService, Clock clock) {
        this.repository = repository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.salesQuery = salesQuery;
        this.businessDay = businessDay;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DailyClosingResponse get(LocalDate date, BigDecimal openingCash) {
        Optional<DailyClosing> stored = repository.findByBusinessDate(date);
        if (stored.isPresent() && stored.get().getStatus() == DayStatus.CLOSED) {
            return frozen(stored.get());
        }
        BigDecimal opening = openingCash == null ? Money.ZERO : Money.round(openingCash);
        SalesFigures figures = salesQuery.forDate(date);
        return new DailyClosingResponse(date, stored.map(DailyClosing::getStatus).orElse(DayStatus.OPEN), true,
                toFigures(figures), cash(figures, opening), null, null, null, activeSessions(date),
                stored.map(d -> username(d.getClosedBy())).orElse(null), stored.map(DailyClosing::getClosedAt).orElse(null),
                stored.map(d -> username(d.getReopenedBy())).orElse(null),
                stored.map(DailyClosing::getReopenedAt).orElse(null),
                stored.map(DailyClosing::getReopenReason).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<ClosingSummary> history(LocalDate from, LocalDate to) {
        return repository.findByBusinessDateBetweenAndStatusInOrderByBusinessDateDesc(from, to,
                        EnumSet.of(DayStatus.CLOSED, DayStatus.REOPENED)).stream()
                .map(d -> new ClosingSummary(d.getBusinessDate(), d.getStatus(), d.getTotalRevenue(),
                        d.getExpectedCash(), d.getActualCash(), d.getCashDifference(), username(d.getClosedBy()),
                        d.getClosedAt()))
                .toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DailyClosingResponse close(LocalDate date, CloseDayRequest request) {
        LocalDate today = businessDay.today();
        if (date.isAfter(today)) {
            throw BusinessException.badRequest("FUTURE_DATE", "A day can only be closed once it has started");
        }
        businessDay.createDayRow(date);
        // Waits for bills being paid on this day right now, and blocks new ones until this transaction ends.
        DailyClosing day = repository.findByBusinessDateForUpdate(date).orElseThrow();
        if (day.getStatus() == DayStatus.CLOSED) {
            throw BusinessException.conflict("DAY_ALREADY_CLOSED", "Business day " + date + " is already closed");
        }
        List<String> running = activeSessions(date);
        if (!running.isEmpty()) {
            throw BusinessException.conflict("SESSIONS_STILL_ACTIVE", running.size() + " session(s) still running ("
                    + String.join(", ", running) + "). End them before closing the day.");
        }
        AuthenticatedUser user = CurrentUser.require();
        SalesFigures f = salesQuery.forDate(date);
        BigDecimal opening = Money.round(request.openingCash());
        BigDecimal expected = f.expectedCash(opening);
        BigDecimal actual = Money.round(request.actualCash());

        day.setGamingRevenue(f.gamingRevenue());
        day.setFoodRevenue(f.foodRevenue());
        day.setRentalRevenue(f.rentalRevenue());
        day.setTotalRevenue(f.netSales());
        day.setCashTotal(f.payments().get(PaymentMode.CASH));
        day.setUpiTotal(f.payments().get(PaymentMode.UPI));
        day.setCardTotal(f.payments().get(PaymentMode.CARD));
        day.setOtherTotal(f.payments().get(PaymentMode.OTHER));
        day.setSessionCount(f.sessionCount());
        day.setBillCount(f.billCount());
        day.setCustomerCount(f.customerCount());
        day.setGamingMinutes(f.gamingMinutes());
        day.setFoodUnitsSold(f.foodUnits());
        day.setDiscountTotal(f.discounts());
        day.setTaxTotal(f.tax());
        day.setRoundOffTotal(f.roundOff());
        day.setRefundTotal(f.totalRefunds());
        day.setDepositTotal(f.totalDeposits());
        day.setDepositRefundTotal(f.totalDepositRefunds());
        day.setOpeningCash(opening);
        day.setExpectedCash(expected);
        day.setActualCash(actual);
        day.setCashDifference(actual.subtract(expected));
        day.setNotes(StringUtils.hasText(request.notes()) ? request.notes().trim() : null);
        day.setStatus(DayStatus.CLOSED);
        day.setClosedBy(user.id());
        day.setClosedAt(clock.instant());
        repository.saveAndFlush(day);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("businessDate", date);
        audit.put("totalRevenue", day.getTotalRevenue());
        audit.put("bills", day.getBillCount());
        audit.put("expectedCash", expected);
        audit.put("actualCash", actual);
        audit.put("cashDifference", day.getCashDifference());
        auditService.record("DAY_CLOSED", "DailyClosing", date, null, audit);
        log.info("Business day {} closed by {}: revenue ₹{}, cash expected ₹{} counted ₹{}", date, user.username(),
                day.getTotalRevenue(), expected, actual);
        return frozen(day);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DailyClosingResponse reopen(LocalDate date, ReopenDayRequest request) {
        DailyClosing day = repository.findByBusinessDateForUpdate(date)
                .filter(d -> d.getStatus() == DayStatus.CLOSED)
                .orElseThrow(() -> BusinessException.conflict("DAY_NOT_CLOSED", "Business day " + date + " is not closed"));
        AuthenticatedUser user = CurrentUser.require();
        String reason = request.reason().trim();
        day.setStatus(DayStatus.REOPENED);
        day.setReopenedBy(user.id());
        day.setReopenedAt(clock.instant());
        day.setReopenReason(reason);
        repository.saveAndFlush(day);
        auditService.record("DAY_REOPENED", "DailyClosing", date,
                Map.of("status", "CLOSED", "totalRevenue", day.getTotalRevenue(), "actualCash", day.getActualCash()),
                Map.of("status", "REOPENED", "reason", reason));
        log.warn("Business day {} reopened by {}: {}", date, user.username(), reason);
        return get(date, day.getOpeningCash());
    }

    // ---------------------------------------------------------------- helpers

    /** Sessions still running that would be paid on {@code date} (only possible for the current business day). */
    private List<String> activeSessions(LocalDate date) {
        if (!date.equals(businessDay.today())) {
            return List.of();
        }
        return sessionRepository.findWithDetailsByStatus(SessionStatus.ACTIVE).stream()
                .map(GamingSession::getStation).map(s -> s.getCode()).toList();
    }

    private DailyClosingResponse frozen(DailyClosing d) {
        ClosingFigures figures = new ClosingFigures(d.getBillCount(), d.getSessionCount(), d.getCustomerCount(),
                d.getGamingMinutes(), d.getFoodUnitsSold(), d.getGamingRevenue(), d.getFoodRevenue(),
                d.getRentalRevenue(), d.getGamingRevenue().add(d.getFoodRevenue()).add(d.getRentalRevenue()),
                d.getDiscountTotal(), d.getTaxTotal(), d.getRoundOffTotal(), d.getTotalRevenue(), d.getCashTotal(),
                d.getUpiTotal(), d.getCardTotal(), d.getOtherTotal(), d.getRefundTotal(), d.getDepositTotal(),
                d.getDepositRefundTotal());
        // The cash split by payment type isn't stored separately; recompute it from the (locked) day's payments.
        SalesFigures live = salesQuery.forDate(d.getBusinessDate());
        CashBreakdown cash = new CashBreakdown(d.getOpeningCash(), live.payments().get(PaymentMode.CASH),
                live.deposits().get(PaymentMode.CASH), live.refunds().get(PaymentMode.CASH),
                live.depositRefunds().get(PaymentMode.CASH), d.getExpectedCash());
        return new DailyClosingResponse(d.getBusinessDate(), d.getStatus(), false, figures, cash, d.getActualCash(),
                d.getCashDifference(), d.getNotes(), List.of(), username(d.getClosedBy()), d.getClosedAt(),
                username(d.getReopenedBy()), d.getReopenedAt(), d.getReopenReason());
    }

    private static ClosingFigures toFigures(SalesFigures f) {
        return new ClosingFigures(f.billCount(), f.sessionCount(), f.customerCount(), f.gamingMinutes(), f.foodUnits(),
                f.gamingRevenue(), f.foodRevenue(), f.rentalRevenue(), f.grossSales(), f.discounts(), f.tax(),
                f.roundOff(), f.netSales(), f.payments().get(PaymentMode.CASH), f.payments().get(PaymentMode.UPI),
                f.payments().get(PaymentMode.CARD), f.payments().get(PaymentMode.OTHER), f.totalRefunds(),
                f.totalDeposits(), f.totalDepositRefunds());
    }

    private static CashBreakdown cash(SalesFigures f, BigDecimal opening) {
        return new CashBreakdown(opening, f.payments().get(PaymentMode.CASH), f.deposits().get(PaymentMode.CASH),
                f.refunds().get(PaymentMode.CASH), f.depositRefunds().get(PaymentMode.CASH), f.expectedCash(opening));
    }

    private String username(Long userId) {
        return userId == null ? null : userRepository.findById(userId).map(User::getUsername).orElse(null);
    }
}
