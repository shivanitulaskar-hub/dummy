package com.consoleadda.service;

import com.consoleadda.dto.PageResponse;
import com.consoleadda.dto.bill.BillDetail;
import com.consoleadda.dto.bill.BillSummary;
import com.consoleadda.dto.bill.CancelBillRequest;
import com.consoleadda.dto.bill.InvoiceResponse;
import com.consoleadda.dto.bill.WalkInBillRequest;
import com.consoleadda.dto.session.SessionItemRequest;
import com.consoleadda.entity.Bill;
import com.consoleadda.entity.BillItem;
import com.consoleadda.entity.BillItemType;
import com.consoleadda.entity.BillStatus;
import com.consoleadda.entity.BillType;
import com.consoleadda.entity.Customer;
import com.consoleadda.entity.Discount;
import com.consoleadda.entity.FoodItem;
import com.consoleadda.entity.GamingSession;
import com.consoleadda.entity.Payment;
import com.consoleadda.entity.PaymentMode;
import com.consoleadda.entity.PaymentType;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.exception.NotFoundException;
import com.consoleadda.repository.BillRepository;
import com.consoleadda.repository.FoodItemRepository;
import com.consoleadda.repository.GamingSessionRepository;
import com.consoleadda.repository.SessionTimeCreditRepository;
import com.consoleadda.security.AuthenticatedUser;
import com.consoleadda.security.CurrentUser;
import com.consoleadda.service.billing.BillCalculator;
import com.consoleadda.service.billing.Money;
import com.consoleadda.service.billing.TaxCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Bills: walk-in food sales, search, detail, invoice data and admin cancellation. */
@Slf4j
@Service
public class BillService {

    private final BillRepository billRepository;
    private final GamingSessionRepository sessionRepository;
    private final SessionTimeCreditRepository creditRepository;
    private final FoodItemRepository foodItemRepository;
    private final FoodService foodService;
    private final InventoryService inventoryService;
    private final CustomerService customerService;
    private final DiscountService discountService;
    private final BillTotalsService totalsService;
    private final BillFinalizer billFinalizer;
    private final BusinessDayService businessDay;
    private final SettingsService settings;
    private final AuditService auditService;
    private final Clock clock;

    public BillService(BillRepository billRepository, GamingSessionRepository sessionRepository,
                       SessionTimeCreditRepository creditRepository, FoodItemRepository foodItemRepository,
                       FoodService foodService, InventoryService inventoryService, CustomerService customerService,
                       DiscountService discountService, BillTotalsService totalsService, BillFinalizer billFinalizer,
                       BusinessDayService businessDay, SettingsService settings, AuditService auditService,
                       Clock clock) {
        this.billRepository = billRepository;
        this.sessionRepository = sessionRepository;
        this.creditRepository = creditRepository;
        this.foodItemRepository = foodItemRepository;
        this.foodService = foodService;
        this.inventoryService = inventoryService;
        this.customerService = customerService;
        this.discountService = discountService;
        this.totalsService = totalsService;
        this.billFinalizer = billFinalizer;
        this.businessDay = businessDay;
        this.settings = settings;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- walk-in sale

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BillDetail createWalkIn(WalkInBillRequest request, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            Optional<Bill> previous = billRepository.findByIdempotencyKey(idempotencyKey);
            if (previous.isPresent()) {
                return detail(previous.get());
            }
        }
        Instant now = clock.instant();
        LocalDate businessDate = businessDay.businessDate(now);
        businessDay.ensureOpen(businessDate);
        Discount discount = discountService.resolve(request.discountId(), businessDate);
        Optional<Customer> customer = customerService.findOrCreate(request.customerName(), request.customerMobile());
        AuthenticatedUser user = CurrentUser.require();

        Bill bill = new Bill();
        bill.setBillType(BillType.WALK_IN);
        bill.setStatus(BillStatus.OPEN);
        bill.setCustomer(customer.orElse(null));
        bill.setCustomerName(customer.map(Customer::getName)
                .orElse(StringUtils.hasText(request.customerName()) ? request.customerName().trim() : null));
        bill.setCustomerMobile(customer.map(Customer::getMobile).orElse(null));
        bill.setCreatedBy(user.id());

        // Merge repeated items into one line each.
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (SessionItemRequest item : request.items()) {
            quantities.merge(item.foodItemId(), item.quantity(), Integer::sum);
        }
        List<FoodItem> foodItems = new ArrayList<>();
        List<BillCalculator.Line> lines = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : quantities.entrySet()) {
            FoodItem food = foodService.sellable(e.getKey());
            foodItems.add(food);
            BillItem line = new BillItem();
            line.setItemType(BillItemType.FOOD);
            line.setFoodItemId(food.getId());
            line.setDescription(food.getName());
            line.setQuantity(e.getValue());
            line.setUnitPrice(food.getSellingPrice());
            line.setLineTotal(Money.round(food.getSellingPrice().multiply(BigDecimal.valueOf(e.getValue()))));
            line.setTaxCategory(TaxCategory.FOOD);
            line.setCreatedBy(user.id());
            bill.addItem(line);
            lines.add(new BillCalculator.Line(TaxCategory.FOOD, food.getName(), e.getValue(), food.getSellingPrice()));
        }
        billRepository.saveAndFlush(bill);
        for (FoodItem food : foodItems) {
            inventoryService.sell(food, quantities.get(food.getId()), bill.getId());
        }
        BillCalculator.Result totals = totalsService.calculate(lines, discount);
        billFinalizer.finalizeBill(bill, totals, discount,
                new BillFinalizer.PaymentInfo(request.paymentMode(), request.referenceNo()),
                idempotencyKey, businessDate, now, user);
        customer.ifPresent(c -> customerService.recordPurchase(c, totals.totalAmount(), now));
        billRepository.saveAndFlush(bill);
        billFinalizer.audit(bill, discount, request.paymentMode(), Map.of("items", quantities.size()));
        log.info("Walk-in bill {} total ₹{} via {} by {}", bill.getBillNumber(), bill.getTotalAmount(),
                request.paymentMode(), user.username());
        return detail(bill);
    }

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public PageResponse<BillSummary> search(String query, LocalDate from, LocalDate to, BillStatus status, int page,
                                            int size) {
        Set<BillStatus> statuses = status == null ? EnumSet.of(BillStatus.PAID, BillStatus.CANCELLED) : EnumSet.of(status);
        String q = StringUtils.hasText(query) ? "%" + query.trim().toLowerCase() + "%" : null;
        var result = billRepository.search(statuses, from, to, q, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 200)));
        return PageResponse.of(result.map(BillService::summary));
    }

    @Transactional(readOnly = true)
    public BillDetail get(Long id) {
        return detail(load(id));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse invoice(Long id) {
        Bill bill = load(id);
        if (bill.getStatus() == BillStatus.OPEN) {
            throw BusinessException.conflict("BILL_NOT_PAID", "An invoice is only available after the bill is paid");
        }
        InvoiceResponse.Business business = new InvoiceResponse.Business(
                settings.getString(SettingKeys.BUSINESS_NAME, "CONSOLE ADDA"),
                settings.getString(SettingKeys.BUSINESS_TAGLINE, "PS5 GAMING CAFE"),
                settings.getString(SettingKeys.BUSINESS_ADDRESS, ""),
                settings.getString(SettingKeys.BUSINESS_PHONE, ""),
                settings.getString(SettingKeys.BUSINESS_GSTIN, ""));
        return new InvoiceResponse(business, detail(bill),
                settings.getString(SettingKeys.INVOICE_FOOTER, "Thank you for gaming with us!"));
    }

    // ---------------------------------------------------------------- cancel (admin)

    /**
     * Cancels a paid bill: refunds every payment in the same mode, puts sold stock back and reverses customer
     * totals. The original bill and its lines are kept unchanged for the record.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BillDetail cancel(Long id, CancelBillRequest request) {
        Bill bill = billRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Bill", id));
        if (bill.getStatus() != BillStatus.PAID) {
            throw BusinessException.conflict("BILL_NOT_PAID", "Only paid bills can be cancelled (this one is "
                    + bill.getStatus().name().toLowerCase() + ")");
        }
        Instant now = clock.instant();
        LocalDate today = businessDay.businessDate(now);
        businessDay.ensureOpen(bill.getBusinessDate());
        businessDay.ensureOpen(today);
        AuthenticatedUser user = CurrentUser.require();
        String reason = request.reason().trim();

        for (BillItem line : bill.getItems()) {
            if (line.getItemType() == BillItemType.FOOD && line.getFoodItemId() != null) {
                foodItemRepository.findById(line.getFoodItemId())
                        .ifPresent(food -> inventoryService.reverseSale(food, line.getQuantity(), bill.getId()));
            }
        }
        for (Payment paid : List.copyOf(bill.getPayments())) {
            if (paid.getPaymentType() != PaymentType.PAYMENT) {
                continue;
            }
            Payment refund = new Payment();
            refund.setPaymentType(PaymentType.REFUND);
            refund.setMode(paid.getMode());
            refund.setAmount(paid.getAmount());
            refund.setReason(reason);
            refund.setBusinessDate(today);
            refund.setReceivedBy(user.id());
            refund.setReceivedAt(now);
            bill.addPayment(refund);
        }
        Optional<GamingSession> session = sessionRepository.findByBillId(bill.getId());
        if (bill.getCustomer() != null) {
            int minutes = session.map(s -> s.getBillableMinutes() == null ? 0 : s.getBillableMinutes()).orElse(0);
            customerService.reverse(bill.getCustomer(), session.isPresent() ? 1 : 0, minutes, bill.getTotalAmount());
        }
        bill.setStatus(BillStatus.CANCELLED);
        bill.setCancelledAt(now);
        bill.setCancelledBy(user.id());
        bill.setCancelReason(reason);
        billRepository.saveAndFlush(bill);
        auditService.record("BILL_CANCELLED", "Bill", bill.getId(),
                Map.of("billNumber", bill.getBillNumber(), "status", "PAID", "totalAmount", bill.getTotalAmount()),
                Map.of("status", "CANCELLED", "reason", reason, "refunded", bill.getTotalAmount()));
        log.info("Bill {} cancelled by {}: {}", bill.getBillNumber(), user.username(), reason);
        return detail(bill);
    }

    // ---------------------------------------------------------------- mapping

    private Bill load(Long id) {
        return billRepository.findById(id).orElseThrow(() -> new NotFoundException("Bill", id));
    }

    static BillSummary summary(Bill b) {
        PaymentMode mode = b.getPayments().stream().filter(p -> p.getPaymentType() == PaymentType.PAYMENT)
                .map(Payment::getMode).findFirst().orElse(null);
        return new BillSummary(b.getId(), b.getBillNumber(), b.getBillType(), b.getStatus(), b.getCustomerName(),
                b.getCustomerMobile(), b.getTotalAmount(), mode, b.getBusinessDate(), b.getPaidAt());
    }

    private BillDetail detail(Bill b) {
        Optional<GamingSession> session = sessionRepository.findByBillId(b.getId());
        BillDetail.Gaming gaming = session.map(s -> new BillDetail.Gaming(s.getId(), s.getStation().getCode(),
                s.getPlayerCount(), s.getStartedAt(), s.getEndedAt(), s.getActualMinutes(), s.getCreditMinutes(),
                s.getBillableMinutes(), s.getPricingPlan().getName())).orElse(null);
        List<BillDetail.Credit> credits = session.map(s -> creditRepository.findBySessionIdOrderByIdAsc(s.getId())
                .stream().map(c -> new BillDetail.Credit(c.getMinutes(), c.getReason())).toList()).orElse(List.of());
        List<BillDetail.Line> lines = b.getItems().stream().map(i -> new BillDetail.Line(i.getId(), i.getItemType().name(),
                i.getDescription(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal())).toList();
        List<BillDetail.Tax> taxes = b.getTaxLines().stream().map(t -> {
            BigDecimal half = t.getTaxAmount().divide(BigDecimal.TWO, Money.SCALE, RoundingMode.HALF_UP);
            return new BillDetail.Tax(t.getTaxCategory(), t.getHsnSacCode(), t.getTaxableValue(), t.getRatePercent(),
                    t.getPriceMode(), t.getTaxAmount(), half, t.getTaxAmount().subtract(half));
        }).toList();
        List<BillDetail.PaymentLine> payments = b.getPayments().stream().map(p -> new BillDetail.PaymentLine(p.getId(),
                p.getPaymentType(), p.getMode(), p.getAmount(), p.getReferenceNo(), p.getReason(), p.getReceivedAt()))
                .toList();
        return new BillDetail(b.getId(), b.getBillNumber(), b.getBillType(), b.getStatus(), b.getCustomerName(),
                b.getCustomerMobile(), b.getBusinessDate(), b.getCreatedAt(), b.getPaidAt(), gaming, lines,
                b.getGamingAmount(), b.getFoodAmount(), b.getRentalAmount(), b.getSubtotal(), b.getDiscountReason(),
                b.getDiscountAmount(), b.getTaxableAmount(), b.getTaxAmount(), taxes, b.getRoundOff(), b.getTotalAmount(),
                payments, credits, b.getCancelledAt(), b.getCancelReason());
    }
}
