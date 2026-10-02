package com.consoleadda.service;

import com.consoleadda.dto.session.CancelSessionRequest;
import com.consoleadda.dto.session.ChargeBreakdown;
import com.consoleadda.dto.session.CompleteSessionRequest;
import com.consoleadda.dto.session.SessionResponse;
import com.consoleadda.dto.session.StartSessionRequest;
import com.consoleadda.dto.session.UpdateSessionRequest;
import com.consoleadda.entity.Bill;
import com.consoleadda.entity.BillItem;
import com.consoleadda.entity.BillItemType;
import com.consoleadda.entity.BillStatus;
import com.consoleadda.entity.BillType;
import com.consoleadda.entity.Customer;
import com.consoleadda.entity.Discount;
import com.consoleadda.entity.GamingSession;
import com.consoleadda.entity.GamingStation;
import com.consoleadda.entity.PricingPlan;
import com.consoleadda.entity.SessionStatus;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.exception.NotFoundException;
import com.consoleadda.mapper.SessionMapper;
import com.consoleadda.repository.BillRepository;
import com.consoleadda.repository.GamingSessionRepository;
import com.consoleadda.repository.GamingStationRepository;
import com.consoleadda.security.AuthenticatedUser;
import com.consoleadda.security.CurrentUser;
import com.consoleadda.service.billing.BillCalculator;
import com.consoleadda.service.billing.TaxCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gaming session lifecycle: start → (live estimate, edits) → END preview → complete (bill + payment) or cancel.
 * <p>
 * Concurrency: starting locks the station row and the database enforces one ACTIVE session per station;
 * completing/cancelling locks the session row, so a double click or two counters can't bill twice.
 * Write operations use READ COMMITTED so that, after waiting for a row lock, they see what the previous
 * holder committed (MySQL's default REPEATABLE READ would keep showing the snapshot from before the wait).
 */
@Slf4j
@Service
public class SessionService {

    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(1);

    private final GamingSessionRepository sessionRepository;
    private final GamingStationRepository stationRepository;
    private final BillRepository billRepository;
    private final PricingService pricingService;
    private final CustomerService customerService;
    private final SessionChargeService chargeService;
    private final BillFinalizer billFinalizer;
    private final DiscountService discountService;
    private final BusinessDayService businessDay;
    private final SettingsService settings;
    private final AuditService auditService;
    private final SessionMapper mapper;
    private final Clock clock;

    public SessionService(GamingSessionRepository sessionRepository, GamingStationRepository stationRepository,
                          BillRepository billRepository, PricingService pricingService, CustomerService customerService,
                          SessionChargeService chargeService, BillFinalizer billFinalizer, DiscountService discountService,
                          BusinessDayService businessDay, SettingsService settings, AuditService auditService,
                          SessionMapper mapper, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.stationRepository = stationRepository;
        this.billRepository = billRepository;
        this.pricingService = pricingService;
        this.customerService = customerService;
        this.chargeService = chargeService;
        this.billFinalizer = billFinalizer;
        this.discountService = discountService;
        this.businessDay = businessDay;
        this.settings = settings;
        this.auditService = auditService;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ start

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SessionResponse start(StartSessionRequest request) {
        Instant now = clock.instant();
        GamingStation station = stationRepository.findByIdForUpdate(request.stationId())
                .orElseThrow(() -> new NotFoundException("Station", request.stationId()));
        if (!station.isActive()) {
            throw BusinessException.conflict("STATION_INACTIVE", station.getCode() + " is deactivated");
        }
        if (sessionRepository.existsByStationIdAndStatus(station.getId(), SessionStatus.ACTIVE)) {
            throw stationOccupied(station);
        }
        PricingPlan plan = pricingService.resolve(businessDay.localDate(now), request.pricingPlanId());
        pricingService.validatePlayers(plan, request.playerCount());
        Optional<Customer> customer = customerService.findOrCreate(request.customerName(), request.customerMobile());
        AuthenticatedUser user = CurrentUser.require();

        Bill bill = new Bill();
        bill.setBillType(BillType.GAMING);
        bill.setStatus(BillStatus.OPEN);
        bill.setCustomer(customer.orElse(null));
        bill.setCustomerName(customerName(request.customerName(), customer));
        bill.setCustomerMobile(customer.map(Customer::getMobile).orElse(null));
        bill.setCreatedBy(user.id());
        billRepository.save(bill);

        GamingSession session = new GamingSession();
        session.setStation(station);
        session.setCustomer(customer.orElse(null));
        session.setBill(bill);
        session.setPricingPlan(plan);
        session.setPlayerCount(request.playerCount());
        session.setStartedAt(now);
        session.setStatus(SessionStatus.ACTIVE);
        session.setNotes(request.notes());
        session.setStartedBy(user.id());
        try {
            sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            // Database backstop (unique active_station_id) in case the application check was bypassed.
            throw stationOccupied(station);
        }
        auditService.record("SESSION_STARTED", "GamingSession", session.getId(), null, Map.of(
                "station", station.getCode(), "players", session.getPlayerCount(), "plan", plan.getName(),
                "customer", String.valueOf(bill.getCustomerName())));
        log.info("Session {} started on {} ({} players, plan '{}') by {}", session.getId(), station.getCode(),
                session.getPlayerCount(), plan.getName(), user.username());
        return mapper.toResponse(session, chargeService.compute(session, now).breakdown());
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public List<SessionResponse> active() {
        Instant now = clock.instant();
        return sessionRepository.findWithDetailsByStatus(SessionStatus.ACTIVE).stream()
                .map(s -> mapper.toResponse(s, chargeService.compute(s, now).breakdown()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse get(Long id) {
        GamingSession session = loadDetailed(id);
        return mapper.toResponse(session, chargesFor(session));
    }

    // ------------------------------------------------------------------ update

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SessionResponse update(Long id, UpdateSessionRequest request) {
        GamingSession session = lockActive(id);
        Map<String, Object> before = snapshot(session);
        if (request.playerCount() != null) {
            pricingService.validatePlayers(session.getPricingPlan(), request.playerCount());
            session.setPlayerCount(request.playerCount());
        }
        Bill bill = session.getBill();
        if (request.customerMobile() != null || request.customerName() != null) {
            String name = request.customerName() != null ? request.customerName() : bill.getCustomerName();
            String mobile = request.customerMobile() != null ? request.customerMobile() : bill.getCustomerMobile();
            Optional<Customer> customer = customerService.findOrCreate(name, mobile);
            session.setCustomer(customer.orElse(null));
            bill.setCustomer(customer.orElse(null));
            bill.setCustomerName(customerName(name, customer));
            bill.setCustomerMobile(customer.map(Customer::getMobile).orElse(null));
        }
        if (request.notes() != null) {
            session.setNotes(request.notes());
        }
        sessionRepository.saveAndFlush(session);
        auditService.record("SESSION_UPDATED", "GamingSession", id, before, snapshot(session));
        return mapper.toResponse(session, chargeService.compute(session, clock.instant()).breakdown());
    }

    // ------------------------------------------------------------------ end preview

    /** Calculates the bill as of now without changing anything; the returned end time is used to complete. */
    @Transactional(readOnly = true)
    public SessionResponse preview(Long id, Long discountId, Instant endedAt) {
        GamingSession session = loadDetailed(id);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw notActive(session);
        }
        Instant now = clock.instant();
        Instant end = endedAt == null ? now : validateEndTime(session, endedAt, now);
        Discount discount = discountService.resolve(discountId, businessDay.businessDate(now));
        return mapper.toResponse(session, chargeService.compute(session, end, discount).breakdown());
    }

    // ------------------------------------------------------------------ complete

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SessionResponse complete(Long id, CompleteSessionRequest request, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            Optional<Bill> previous = billRepository.findByIdempotencyKey(idempotencyKey);
            if (previous.isPresent()) {
                return replay(id, previous.get());
            }
        }
        GamingSession session;
        try {
            session = lockActive(id);
        } catch (BusinessException e) {
            // A retry that waited for the lock while the original request completed the bill.
            if (StringUtils.hasText(idempotencyKey) && "SESSION_NOT_ACTIVE".equals(e.getCode())) {
                Optional<Bill> previous = billRepository.findByIdempotencyKey(idempotencyKey);
                if (previous.isPresent()) {
                    return replay(id, previous.get());
                }
            }
            throw e;
        }
        Instant now = clock.instant();
        Instant endedAt = validateEndTime(session, request.endedAt(), now);
        LocalDate businessDate = businessDay.businessDate(now);
        businessDay.ensureOpen(businessDate);

        Discount discount = discountService.resolve(request.discountId(), businessDate);
        SessionChargeService.Computed computed = chargeService.compute(session, endedAt, discount);
        BillCalculator.Result totals = computed.totals();
        AuthenticatedUser user = CurrentUser.require();

        session.setEndedAt(endedAt);
        session.setActualMinutes(computed.actualMinutes());
        session.setBillableMinutes(computed.billableMinutes());
        session.setGamingAmount(computed.gaming().total());
        session.setStatus(SessionStatus.COMPLETED);
        session.setEndedBy(user.id());

        Bill bill = session.getBill();
        bill.getItems().removeIf(i -> i.getItemType() == BillItemType.GAMING);
        BillItem gamingLine = new BillItem();
        gamingLine.setItemType(BillItemType.GAMING);
        gamingLine.setDescription(computed.gamingDescription());
        gamingLine.setQuantity(1);
        gamingLine.setUnitPrice(computed.gaming().total());
        gamingLine.setLineTotal(computed.gaming().total());
        gamingLine.setTaxCategory(TaxCategory.GAMING);
        gamingLine.setCreatedBy(user.id());
        bill.addItem(gamingLine);

        billFinalizer.finalizeBill(bill, totals, discount,
                new BillFinalizer.PaymentInfo(request.paymentMode(), request.referenceNo()),
                idempotencyKey, businessDate, now, user);
        if (session.getCustomer() != null) {
            customerService.recordVisit(session.getCustomer(), computed.billableMinutes(), totals.totalAmount(), endedAt);
        }
        sessionRepository.saveAndFlush(session);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("station", session.getStation().getCode());
        context.put("billableMinutes", computed.billableMinutes());
        context.put("creditMinutes", computed.creditMinutes());
        billFinalizer.audit(bill, discount, request.paymentMode(), context);
        log.info("Session {} completed: bill {} total ₹{} via {} by {}", id, bill.getBillNumber(),
                bill.getTotalAmount(), request.paymentMode(), user.username());
        return mapper.toResponse(session, chargeService.stored(session));
    }

    // ------------------------------------------------------------------ cancel

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SessionResponse cancel(Long id, CancelSessionRequest request) {
        GamingSession session = lockActive(id);
        AuthenticatedUser user = CurrentUser.require();
        Instant now = clock.instant();
        if (!user.isAdmin()) {
            int window = settings.getInt(SettingKeys.OPERATOR_CANCEL_WINDOW, 15);
            if (Duration.between(session.getStartedAt(), now).toMinutes() >= window) {
                throw BusinessException.forbidden("CANCEL_WINDOW_PASSED", "Sessions older than " + window
                        + " minutes can only be cancelled by an admin. End the session normally or ask an admin.");
            }
        }
        if (session.getBill().getItems().stream().anyMatch(i -> i.getItemType() != BillItemType.GAMING)) {
            throw BusinessException.conflict("SESSION_HAS_ITEMS",
                    "Remove food/other items from this session before cancelling it, or end it normally.");
        }
        session.setStatus(SessionStatus.CANCELLED);
        session.setEndedAt(now);
        session.setActualMinutes((int) Duration.between(session.getStartedAt(), now).toMinutes());
        session.setCancelReason(request.reason().trim());
        session.setEndedBy(user.id());
        Bill bill = session.getBill();
        bill.setStatus(BillStatus.CANCELLED);
        bill.setCancelledAt(now);
        bill.setCancelledBy(user.id());
        bill.setCancelReason(request.reason().trim());
        sessionRepository.saveAndFlush(session);
        auditService.record("SESSION_CANCELLED", "GamingSession", id, Map.of("status", "ACTIVE"),
                Map.of("status", "CANCELLED", "reason", request.reason().trim(),
                        "station", session.getStation().getCode(), "minutesPlayed", session.getActualMinutes()));
        log.info("Session {} on {} cancelled by {}: {}", id, session.getStation().getCode(), user.username(),
                request.reason());
        return mapper.toResponse(session, null);
    }

    // ------------------------------------------------------------------ helpers

    private SessionResponse replay(Long sessionId, Bill bill) {
        GamingSession session = loadDetailed(sessionId);
        if (!session.getBill().getId().equals(bill.getId())) {
            throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED", "This request key was already used for another bill");
        }
        log.info("Replaying completed session {} for repeated request", sessionId);
        return mapper.toResponse(session, chargeService.stored(session));
    }

    private Instant validateEndTime(GamingSession session, Instant requested, Instant now) {
        Instant endedAt = requested == null ? now : requested;
        if (endedAt.isAfter(now.plus(FUTURE_TOLERANCE))) {
            throw BusinessException.badRequest("INVALID_END_TIME", "End time cannot be in the future");
        }
        int maxBackdate = settings.getInt(SettingKeys.MAX_END_BACKDATE, 30);
        if (endedAt.isBefore(now.minus(Duration.ofMinutes(maxBackdate)))) {
            throw BusinessException.badRequest("INVALID_END_TIME", "End time is more than " + maxBackdate
                    + " minutes old. Press END again to recalculate.");
        }
        if (endedAt.isBefore(session.getStartedAt())) {
            throw BusinessException.badRequest("INVALID_END_TIME", "End time is before the session start");
        }
        return endedAt.isAfter(now) ? now : endedAt;
    }

    private GamingSession lockActive(Long id) {
        sessionRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Session", id));
        GamingSession session = loadDetailed(id);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw notActive(session);
        }
        return session;
    }

    private GamingSession loadDetailed(Long id) {
        return sessionRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Session", id));
    }

    private ChargeBreakdown chargesFor(GamingSession session) {
        return switch (session.getStatus()) {
            case ACTIVE -> chargeService.compute(session, clock.instant()).breakdown();
            case COMPLETED -> chargeService.stored(session);
            case CANCELLED -> null;
        };
    }

    private static Map<String, Object> snapshot(GamingSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("players", s.getPlayerCount());
        m.put("customerName", s.getBill().getCustomerName());
        m.put("customerMobile", s.getBill().getCustomerMobile());
        m.put("notes", s.getNotes());
        return m;
    }

    private static String customerName(String requested, Optional<Customer> customer) {
        if (customer.isPresent()) {
            return customer.get().getName();
        }
        return StringUtils.hasText(requested) ? requested.trim() : null;
    }

    private static BusinessException stationOccupied(GamingStation station) {
        return BusinessException.conflict("STATION_OCCUPIED", station.getCode() + " already has an active session");
    }

    private static BusinessException notActive(GamingSession session) {
        String detail = session.getStatus() == SessionStatus.COMPLETED && session.getBill().getBillNumber() != null
                ? " (bill " + session.getBill().getBillNumber() + ")" : "";
        return BusinessException.conflict("SESSION_NOT_ACTIVE",
                "Session is already " + session.getStatus().name().toLowerCase() + detail);
    }
}
