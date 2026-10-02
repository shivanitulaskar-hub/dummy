package com.consoleadda.service;

import com.consoleadda.dto.session.SessionItemRequest;
import com.consoleadda.dto.session.SessionResponse;
import com.consoleadda.entity.Bill;
import com.consoleadda.entity.BillItem;
import com.consoleadda.entity.BillItemType;
import com.consoleadda.entity.BillStatus;
import com.consoleadda.entity.FoodItem;
import com.consoleadda.entity.GamingSession;
import com.consoleadda.entity.SessionStatus;
import com.consoleadda.entity.SessionTimeCredit;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.exception.NotFoundException;
import com.consoleadda.mapper.SessionMapper;
import com.consoleadda.repository.BillRepository;
import com.consoleadda.repository.FoodItemRepository;
import com.consoleadda.repository.GamingSessionRepository;
import com.consoleadda.repository.SessionTimeCreditRepository;
import com.consoleadda.security.AuthenticatedUser;
import com.consoleadda.security.CurrentUser;
import com.consoleadda.service.billing.Money;
import com.consoleadda.service.billing.TaxCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/**
 * Food & drinks on a running session's bill. Locks the session row first, then the bill (the same order as
 * completing a session), so adding food and completing the bill can never interleave.
 * <p>
 * Free gaming time: each line of an item with {@code gaming_credit_minutes} earns credit (minutes × quantity),
 * capped per session by {@code gaming.credit.max-minutes-per-session}. Credits are recalculated from the bill's
 * lines after every change and stored with their source line and reason.
 */
@Service
public class SessionItemService {

    private final GamingSessionRepository sessionRepository;
    private final BillRepository billRepository;
    private final FoodItemRepository foodItemRepository;
    private final SessionTimeCreditRepository creditRepository;
    private final FoodService foodService;
    private final InventoryService inventoryService;
    private final SessionChargeService chargeService;
    private final SettingsService settings;
    private final AuditService auditService;
    private final SessionMapper mapper;
    private final Clock clock;

    public SessionItemService(GamingSessionRepository sessionRepository, BillRepository billRepository,
                              FoodItemRepository foodItemRepository, SessionTimeCreditRepository creditRepository,
                              FoodService foodService, InventoryService inventoryService,
                              SessionChargeService chargeService, SettingsService settings, AuditService auditService,
                              SessionMapper mapper, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.billRepository = billRepository;
        this.foodItemRepository = foodItemRepository;
        this.creditRepository = creditRepository;
        this.foodService = foodService;
        this.inventoryService = inventoryService;
        this.chargeService = chargeService;
        this.settings = settings;
        this.auditService = auditService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SessionResponse addItem(Long sessionId, SessionItemRequest request) {
        Locked locked = lock(sessionId);
        FoodItem item = foodService.sellable(request.foodItemId());
        Long userId = CurrentUser.require().id();
        BillItem line = locked.bill.getItems().stream()
                .filter(i -> i.getItemType() == BillItemType.FOOD && Objects.equals(i.getFoodItemId(), item.getId())
                        && i.getUnitPrice().compareTo(item.getSellingPrice()) == 0)
                .findFirst().orElse(null);
        if (line == null) {
            line = new BillItem();
            line.setItemType(BillItemType.FOOD);
            line.setFoodItemId(item.getId());
            line.setDescription(item.getName());
            line.setUnitPrice(item.getSellingPrice());
            line.setTaxCategory(TaxCategory.FOOD);
            line.setCreatedBy(userId);
            line.setQuantity(0);
            locked.bill.addItem(line);
        }
        setQuantity(line, line.getQuantity() + request.quantity());
        inventoryService.sell(item, request.quantity(), locked.bill.getId());
        return finish(locked);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SessionResponse changeQuantity(Long sessionId, Long lineId, int quantity) {
        Locked locked = lock(sessionId);
        BillItem line = foodLine(locked.bill, lineId);
        FoodItem item = foodItem(line);
        int delta = quantity - line.getQuantity();
        if (delta > 0) {
            inventoryService.sell(item, delta, locked.bill.getId());
        } else if (delta < 0) {
            inventoryService.reverseSale(item, -delta, locked.bill.getId());
            auditService.record("FOOD_ITEM_REDUCED", "Bill", locked.bill.getId(),
                    Map.of("item", line.getDescription(), "quantity", line.getQuantity()),
                    Map.of("item", line.getDescription(), "quantity", quantity));
        }
        setQuantity(line, quantity);
        return finish(locked);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SessionResponse removeItem(Long sessionId, Long lineId) {
        Locked locked = lock(sessionId);
        BillItem line = foodLine(locked.bill, lineId);
        // Credits reference the line; drop them before the line goes.
        creditRepository.deleteBySessionId(locked.session.getId());
        inventoryService.reverseSale(foodItem(line), line.getQuantity(), locked.bill.getId());
        auditService.record("FOOD_ITEM_REMOVED", "Bill", locked.bill.getId(),
                Map.of("item", line.getDescription(), "quantity", line.getQuantity(), "lineTotal", line.getLineTotal()),
                null);
        locked.bill.getItems().remove(line);
        return finish(locked);
    }

    // ---------------------------------------------------------------- helpers

    private record Locked(GamingSession session, Bill bill) {
    }

    private Locked lock(Long sessionId) {
        GamingSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new NotFoundException("Session", sessionId));
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw BusinessException.conflict("SESSION_NOT_ACTIVE", "Items can only be changed while the session is active");
        }
        Bill bill = billRepository.findByIdForUpdate(session.getBill().getId()).orElseThrow();
        if (bill.getStatus() != BillStatus.OPEN) {
            throw BusinessException.conflict("BILL_NOT_OPEN", "This bill is already closed");
        }
        return new Locked(session, bill);
    }

    private SessionResponse finish(Locked locked) {
        billRepository.saveAndFlush(locked.bill);
        recomputeCredits(locked.session, locked.bill);
        sessionRepository.saveAndFlush(locked.session);
        GamingSession fresh = sessionRepository.findWithDetailsById(locked.session.getId()).orElseThrow();
        return mapper.toResponse(fresh, chargeService.compute(fresh, clock.instant()).breakdown());
    }

    void recomputeCredits(GamingSession session, Bill bill) {
        creditRepository.deleteBySessionId(session.getId());
        int remaining = settings.getInt(SettingKeys.CREDIT_MAX_MINUTES, 15);
        int granted = 0;
        AuthenticatedUser user = CurrentUser.require();
        for (BillItem line : bill.getItems()) {
            if (line.getItemType() != BillItemType.FOOD || line.getFoodItemId() == null || remaining <= 0) {
                continue;
            }
            FoodItem item = foodItemRepository.findById(line.getFoodItemId()).orElse(null);
            if (item == null || item.getGamingCreditMinutes() <= 0) {
                continue;
            }
            int earned = Math.min(item.getGamingCreditMinutes() * line.getQuantity(), remaining);
            SessionTimeCredit credit = new SessionTimeCredit();
            credit.setSession(session);
            credit.setSourceBillItem(line);
            credit.setMinutes(earned);
            credit.setReason("Free gaming time with " + line.getDescription() + " × " + line.getQuantity());
            credit.setCreatedBy(user.id());
            creditRepository.save(credit);
            remaining -= earned;
            granted += earned;
        }
        session.setCreditMinutes(granted);
    }

    private static void setQuantity(BillItem line, int quantity) {
        line.setQuantity(quantity);
        line.setLineTotal(Money.round(line.getUnitPrice().multiply(BigDecimal.valueOf(quantity))));
    }

    private static BillItem foodLine(Bill bill, Long lineId) {
        return bill.getItems().stream()
                .filter(i -> i.getId().equals(lineId))
                .filter(i -> i.getItemType() == BillItemType.FOOD)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Food line", lineId));
    }

    private FoodItem foodItem(BillItem line) {
        return foodItemRepository.findById(line.getFoodItemId())
                .orElseThrow(() -> new NotFoundException("Menu item", line.getFoodItemId()));
    }
}
