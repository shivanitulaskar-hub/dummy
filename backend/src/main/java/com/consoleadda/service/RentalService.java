package com.consoleadda.service;

import com.consoleadda.dto.PageResponse;
import com.consoleadda.dto.rental.CancelRentalRequest;
import com.consoleadda.dto.rental.RentalRequest;
import com.consoleadda.dto.rental.RentalResponse;
import com.consoleadda.dto.rental.ReturnPreview;
import com.consoleadda.dto.rental.ReturnRentalRequest;
import com.consoleadda.entity.Bill;
import com.consoleadda.entity.BillItem;
import com.consoleadda.entity.BillItemType;
import com.consoleadda.entity.BillStatus;
import com.consoleadda.entity.BillType;
import com.consoleadda.entity.Customer;
import com.consoleadda.entity.GameMaster;
import com.consoleadda.entity.GameRental;
import com.consoleadda.entity.Payment;
import com.consoleadda.entity.PaymentMode;
import com.consoleadda.entity.PaymentType;
import com.consoleadda.entity.RentalStatus;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.exception.NotFoundException;
import com.consoleadda.mapper.RentalMapper;
import com.consoleadda.repository.BillRepository;
import com.consoleadda.repository.GameMasterRepository;
import com.consoleadda.repository.GameRentalRepository;
import com.consoleadda.security.AuthenticatedUser;
import com.consoleadda.security.CurrentUser;
import com.consoleadda.service.billing.BillCalculator;
import com.consoleadda.service.billing.Money;
import com.consoleadda.service.billing.RentalCalculator;
import com.consoleadda.service.billing.TaxCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Game rentals: rent out → (overdue) → return with late fee, or cancel.
 * <p>
 * Money: the rental charge is a RENTAL bill paid at the counter; the deposit is a DEPOSIT payment on that bill
 * (not revenue) and is given back as a DEPOSIT_REFUND in the same mode on return. A late fee is charged on a
 * separate bill at return time, so paid bills are never edited.
 * <p>
 * Concurrency: renting locks the game row and the database allows one OUT rental per disc (unique generated
 * column); returning/cancelling locks the rental row.
 */
@Slf4j
@Service
public class RentalService {

    private final GameRentalRepository rentalRepository;
    private final GameMasterRepository gameRepository;
    private final BillRepository billRepository;
    private final CustomerService customerService;
    private final BillTotalsService totalsService;
    private final BillFinalizer billFinalizer;
    private final BillNumberService numberService;
    private final BusinessDayService businessDay;
    private final SettingsService settings;
    private final AuditService auditService;
    private final RentalMapper mapper;
    private final Clock clock;

    public RentalService(GameRentalRepository rentalRepository, GameMasterRepository gameRepository,
                         BillRepository billRepository, CustomerService customerService,
                         BillTotalsService totalsService, BillFinalizer billFinalizer, BillNumberService numberService,
                         BusinessDayService businessDay, SettingsService settings, AuditService auditService,
                         RentalMapper mapper, Clock clock) {
        this.rentalRepository = rentalRepository;
        this.gameRepository = gameRepository;
        this.billRepository = billRepository;
        this.customerService = customerService;
        this.totalsService = totalsService;
        this.billFinalizer = billFinalizer;
        this.numberService = numberService;
        this.businessDay = businessDay;
        this.settings = settings;
        this.auditService = auditService;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- rent out

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RentalResponse create(RentalRequest request, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            Optional<GameRental> previous = billRepository.findByIdempotencyKey(idempotencyKey)
                    .flatMap(b -> rentalRepository.findByBillId(b.getId()));
            if (previous.isPresent()) {
                return mapper.toResponse(previous.get());
            }
        }
        Instant now = clock.instant();
        LocalDate businessDate = businessDay.businessDate(now);
        businessDay.ensureOpen(businessDate);
        GameMaster game = gameRepository.findByIdForUpdate(request.gameId())
                .orElseThrow(() -> new NotFoundException("Game", request.gameId()));
        if (!game.isActive() || !game.isAvailableForRental()) {
            throw BusinessException.conflict("GAME_NOT_AVAILABLE", label(game) + " is not available for rental");
        }
        if (rentalRepository.existsByGameIdAndStatus(game.getId(), RentalStatus.OUT)) {
            throw alreadyRented(game);
        }
        Customer customer = customerService.findOrCreate(request.customerName(), request.customerMobile())
                .orElseThrow(() -> BusinessException.badRequest("MOBILE_REQUIRED", "A mobile number is required to rent a game"));
        int days = request.rentalDays() != null ? request.rentalDays() : game.getRentalPeriodDays();
        BigDecimal deposit = request.depositAmount() != null ? Money.round(request.depositAmount()) : game.getDefaultDeposit();
        BigDecimal price = RentalCalculator.rentalPrice(game.getRentalPrice(), game.getRentalPeriodDays(), days);
        AuthenticatedUser user = CurrentUser.require();
        String rentalNumber = numberService.next(settings.getString(SettingKeys.RENTAL_NUMBER_PREFIX, "RN"), businessDate);

        Bill bill = newBill(customer, user);
        String description = "Game rental " + rentalNumber + ": " + label(game) + ", " + days + (days == 1 ? " day" : " days");
        addLine(bill, BillItemType.RENTAL, description, 1, price, user);
        billRepository.saveAndFlush(bill);
        BillCalculator.Result totals = totalsService.calculate(
                List.of(new BillCalculator.Line(TaxCategory.RENTAL, description, 1, price)), null);
        billFinalizer.finalizeBill(bill, totals, null,
                new BillFinalizer.PaymentInfo(request.paymentMode(), request.referenceNo()),
                idempotencyKey, businessDate, now, user);
        if (deposit.signum() > 0) {
            bill.addPayment(payment(PaymentType.DEPOSIT, request.paymentMode(), deposit, request.referenceNo(),
                    "Deposit for " + rentalNumber, businessDate, now, user));
        }

        GameRental rental = new GameRental();
        rental.setRentalNumber(rentalNumber);
        rental.setGame(game);
        rental.setCustomer(customer);
        rental.setCustomerName(customer.getName());
        rental.setCustomerMobile(customer.getMobile());
        rental.setBill(bill);
        rental.setRentalDays(days);
        rental.setRentalPrice(price);
        rental.setDepositAmount(deposit);
        rental.setOutAt(now);
        rental.setExpectedReturnAt(now.plus(Duration.ofDays(days)));
        rental.setTotalAmount(totals.totalAmount());
        rental.setPaymentMode(request.paymentMode());
        rental.setStatus(RentalStatus.OUT);
        rental.setNotes(StringUtils.hasText(request.notes()) ? request.notes().trim() : null);
        rental.setCreatedBy(user.id());
        try {
            rentalRepository.saveAndFlush(rental);
        } catch (DataIntegrityViolationException e) {
            // Database backstop (unique active_game_id) in case the application check was bypassed.
            throw alreadyRented(game);
        }
        customerService.recordPurchase(customer, totals.totalAmount(), now);
        billRepository.saveAndFlush(bill);
        billFinalizer.audit(bill, null, request.paymentMode(), Map.of("rentalNumber", rentalNumber));
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("rentalNumber", rentalNumber);
        audit.put("game", label(game));
        audit.put("customer", customer.getName());
        audit.put("mobile", customer.getMobile());
        audit.put("days", days);
        audit.put("rentalPrice", price);
        audit.put("deposit", deposit);
        audit.put("expectedReturnAt", rental.getExpectedReturnAt());
        auditService.record("RENTAL_CREATED", "GameRental", rental.getId(), null, audit);
        log.info("Rental {} created: {} to {} for {} days, ₹{} + deposit ₹{} by {}", rentalNumber, label(game),
                customer.getMobile(), days, totals.totalAmount(), deposit, user.username());
        return mapper.toResponse(rental, now, mapper.grace());
    }

    // ---------------------------------------------------------------- return

    @Transactional(readOnly = true)
    public ReturnPreview previewReturn(Long id) {
        GameRental rental = loadDetailed(id);
        requireOut(rental);
        Instant now = clock.instant();
        int lateDays = RentalCalculator.lateDays(rental.getExpectedReturnAt(), now, mapper.grace());
        BigDecimal perDay = rental.getGame().getLateFeePerDay();
        BigDecimal fee = RentalCalculator.lateFee(lateDays, perDay);
        BigDecimal feeTotal = fee.signum() > 0
                ? totalsService.calculate(List.of(lateFeeLine(rental, lateDays)), null).totalAmount() : Money.ZERO;
        return new ReturnPreview(id, rental.getExpectedReturnAt(), now, lateDays, perDay, fee, feeTotal,
                rental.getDepositAmount(), depositMode(rental));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RentalResponse returnGame(Long id, ReturnRentalRequest request) {
        GameRental rental = lockOut(id);
        Instant now = clock.instant();
        LocalDate businessDate = businessDay.businessDate(now);
        businessDay.ensureOpen(businessDate);
        AuthenticatedUser user = CurrentUser.require();
        int lateDays = RentalCalculator.lateDays(rental.getExpectedReturnAt(), now, mapper.grace());
        BigDecimal fee = RentalCalculator.lateFee(lateDays, rental.getGame().getLateFeePerDay());

        if (fee.signum() > 0) {
            PaymentMode mode = request == null ? null : request.paymentMode();
            if (mode == null) {
                throw BusinessException.badRequest("PAYMENT_MODE_REQUIRED", "A late fee of ₹" + fee
                        + " is due. Select how it is paid.");
            }
            Bill lateBill = newBill(rental.getCustomer(), user);
            BillCalculator.Line line = lateFeeLine(rental, lateDays);
            addLine(lateBill, BillItemType.LATE_FEE, line.description(), lateDays, line.unitPrice(), user);
            billRepository.saveAndFlush(lateBill);
            BillCalculator.Result totals = totalsService.calculate(List.of(line), null);
            billFinalizer.finalizeBill(lateBill, totals, null,
                    new BillFinalizer.PaymentInfo(mode, request.referenceNo()), null, businessDate, now, user);
            if (rental.getCustomer() != null) {
                customerService.recordPurchase(rental.getCustomer(), totals.totalAmount(), now);
            }
            billRepository.saveAndFlush(lateBill);
            billFinalizer.audit(lateBill, null, mode, Map.of("rentalNumber", rental.getRentalNumber(),
                    "lateDays", lateDays));
            rental.setLateFeeBill(lateBill);
            rental.setTotalAmount(rental.getTotalAmount().add(totals.totalAmount()));
        }
        BigDecimal deposit = rental.getDepositAmount();
        if (deposit.signum() > 0) {
            rental.getBill().addPayment(payment(PaymentType.DEPOSIT_REFUND, depositMode(rental), deposit, null,
                    "Deposit returned for " + rental.getRentalNumber(), businessDate, now, user));
            rental.setDepositRefunded(deposit);
        }
        rental.setReturnedAt(now);
        rental.setLateDays(lateDays);
        rental.setLateFee(fee);
        rental.setStatus(RentalStatus.RETURNED);
        rental.setReturnedBy(user.id());
        if (request != null && StringUtils.hasText(request.notes())) {
            rental.setNotes(join(rental.getNotes(), "Return: " + request.notes().trim()));
        }
        rentalRepository.saveAndFlush(rental);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("rentalNumber", rental.getRentalNumber());
        audit.put("game", label(rental.getGame()));
        audit.put("lateDays", lateDays);
        audit.put("lateFee", fee);
        audit.put("depositRefunded", rental.getDepositRefunded());
        auditService.record("RENTAL_RETURNED", "GameRental", id, Map.of("status", "OUT"), audit);
        log.info("Rental {} returned: {} late day(s), fee ₹{}, deposit ₹{} refunded, by {}", rental.getRentalNumber(),
                lateDays, fee, rental.getDepositRefunded(), user.username());
        return mapper.toResponse(rental, now, mapper.grace());
    }

    // ---------------------------------------------------------------- cancel

    /**
     * Undo a rental entered by mistake: the rental charge and the deposit are refunded in the mode they were
     * paid. Operators can do this shortly after renting out; admins any time while the disc is out.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RentalResponse cancel(Long id, CancelRentalRequest request) {
        GameRental rental = lockOut(id);
        AuthenticatedUser user = CurrentUser.require();
        Instant now = clock.instant();
        if (!user.isAdmin()) {
            int window = settings.getInt(SettingKeys.OPERATOR_CANCEL_WINDOW, 15);
            if (Duration.between(rental.getOutAt(), now).toMinutes() >= window) {
                throw BusinessException.forbidden("CANCEL_WINDOW_PASSED", "Rentals older than " + window
                        + " minutes can only be cancelled by an admin. Use Return instead, or ask an admin.");
            }
        }
        Bill bill = rental.getBill();
        LocalDate today = businessDay.businessDate(now);
        businessDay.ensureOpen(bill.getBusinessDate());
        businessDay.ensureOpen(today);
        String reason = request.reason().trim();
        for (Payment paid : List.copyOf(bill.getPayments())) {
            PaymentType reverse = switch (paid.getPaymentType()) {
                case PAYMENT -> PaymentType.REFUND;
                case DEPOSIT -> PaymentType.DEPOSIT_REFUND;
                default -> null;
            };
            if (reverse != null) {
                bill.addPayment(payment(reverse, paid.getMode(), paid.getAmount(), null, reason, today, now, user));
            }
        }
        bill.setStatus(BillStatus.CANCELLED);
        bill.setCancelledAt(now);
        bill.setCancelledBy(user.id());
        bill.setCancelReason(reason);
        if (rental.getCustomer() != null) {
            customerService.reverse(rental.getCustomer(), 0, 0, bill.getTotalAmount());
        }
        rental.setStatus(RentalStatus.CANCELLED);
        rental.setDepositRefunded(rental.getDepositAmount());
        rental.setTotalAmount(Money.ZERO);
        rental.setNotes(join(rental.getNotes(), "Cancelled: " + reason));
        rentalRepository.saveAndFlush(rental);
        auditService.record("BILL_CANCELLED", "Bill", bill.getId(),
                Map.of("billNumber", bill.getBillNumber(), "status", "PAID", "totalAmount", bill.getTotalAmount()),
                Map.of("status", "CANCELLED", "reason", reason, "refunded", bill.getTotalAmount()));
        auditService.record("RENTAL_CANCELLED", "GameRental", id, Map.of("status", "OUT"),
                Map.of("status", "CANCELLED", "reason", reason, "rentalNumber", rental.getRentalNumber()));
        log.info("Rental {} cancelled by {}: {}", rental.getRentalNumber(), user.username(), reason);
        return mapper.toResponse(rental, now, mapper.grace());
    }

    // ---------------------------------------------------------------- read

    /** {@code status}: OUT (all discs out, overdue included), OVERDUE, RETURNED, CANCELLED, or null for all. */
    @Transactional(readOnly = true)
    public PageResponse<RentalResponse> search(RentalStatus status, String query, int page, int size) {
        Instant now = clock.instant();
        Duration grace = mapper.grace();
        Set<RentalStatus> statuses = status == null
                ? EnumSet.of(RentalStatus.OUT, RentalStatus.RETURNED, RentalStatus.CANCELLED)
                : EnumSet.of(status == RentalStatus.OVERDUE ? RentalStatus.OUT : status);
        Instant overdueBefore = status == RentalStatus.OVERDUE ? now.minus(grace) : null;
        String q = StringUtils.hasText(query) ? "%" + query.trim().toLowerCase() + "%" : null;
        var result = rentalRepository.search(statuses, overdueBefore, q,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));
        return PageResponse.of(result.map(r -> mapper.toResponse(r, now, grace)));
    }

    @Transactional(readOnly = true)
    public RentalResponse get(Long id) {
        return mapper.toResponse(loadDetailed(id));
    }

    // ---------------------------------------------------------------- helpers

    private GameRental lockOut(Long id) {
        rentalRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Rental", id));
        GameRental rental = loadDetailed(id);
        requireOut(rental);
        return rental;
    }

    private GameRental loadDetailed(Long id) {
        return rentalRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Rental", id));
    }

    private static void requireOut(GameRental rental) {
        if (rental.getStatus() != RentalStatus.OUT) {
            throw BusinessException.conflict("RENTAL_NOT_OUT", "Rental " + rental.getRentalNumber() + " is already "
                    + rental.getStatus().name().toLowerCase());
        }
    }

    private static BillCalculator.Line lateFeeLine(GameRental rental, int lateDays) {
        return new BillCalculator.Line(TaxCategory.RENTAL, "Late fee " + rental.getRentalNumber() + ": "
                + label(rental.getGame()) + ", " + lateDays + (lateDays == 1 ? " day" : " days") + " late",
                lateDays, rental.getGame().getLateFeePerDay());
    }

    /** The deposit goes back in the mode it was received. */
    private static PaymentMode depositMode(GameRental rental) {
        return rental.getBill().getPayments().stream().filter(p -> p.getPaymentType() == PaymentType.DEPOSIT)
                .map(Payment::getMode).findFirst().orElse(rental.getPaymentMode());
    }

    private static Bill newBill(Customer customer, AuthenticatedUser user) {
        Bill bill = new Bill();
        bill.setBillType(BillType.RENTAL);
        bill.setStatus(BillStatus.OPEN);
        bill.setCustomer(customer);
        bill.setCustomerName(customer == null ? null : customer.getName());
        bill.setCustomerMobile(customer == null ? null : customer.getMobile());
        bill.setCreatedBy(user.id());
        return bill;
    }

    private static void addLine(Bill bill, BillItemType type, String description, int quantity, BigDecimal unitPrice,
                                AuthenticatedUser user) {
        BillItem line = new BillItem();
        line.setItemType(type);
        line.setDescription(description.length() > 200 ? description.substring(0, 200) : description);
        line.setQuantity(quantity);
        line.setUnitPrice(unitPrice);
        line.setLineTotal(Money.round(unitPrice.multiply(BigDecimal.valueOf(quantity))));
        line.setTaxCategory(TaxCategory.RENTAL);
        line.setCreatedBy(user.id());
        bill.addItem(line);
    }

    private static Payment payment(PaymentType type, PaymentMode mode, BigDecimal amount, String referenceNo,
                                   String reason, LocalDate businessDate, Instant at, AuthenticatedUser user) {
        Payment p = new Payment();
        p.setPaymentType(type);
        p.setMode(mode);
        p.setAmount(amount);
        p.setReferenceNo(StringUtils.hasText(referenceNo) ? referenceNo.trim() : null);
        p.setReason(reason);
        p.setBusinessDate(businessDate);
        p.setReceivedBy(user.id());
        p.setReceivedAt(at);
        return p;
    }

    private static String label(GameMaster game) {
        return game.getCopyNumber() > 1 ? game.getName() + " (copy " + game.getCopyNumber() + ")" : game.getName();
    }

    private static String join(String existing, String addition) {
        String joined = StringUtils.hasText(existing) ? existing + " | " + addition : addition;
        return joined.length() > 500 ? joined.substring(0, 500) : joined;
    }

    private BusinessException alreadyRented(GameMaster game) {
        return BusinessException.conflict("GAME_ALREADY_RENTED", label(game) + " is already rented out");
    }
}
