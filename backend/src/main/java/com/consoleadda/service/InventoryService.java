package com.consoleadda.service;

import com.consoleadda.dto.inventory.InventoryTxnRequest;
import com.consoleadda.dto.inventory.InventoryTxnResponse;
import com.consoleadda.dto.inventory.StockRow;
import com.consoleadda.entity.FoodItem;
import com.consoleadda.entity.InventoryTransaction;
import com.consoleadda.entity.InventoryTxnType;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.exception.NotFoundException;
import com.consoleadda.repository.FoodItemRepository;
import com.consoleadda.repository.InventoryTransactionRepository;
import com.consoleadda.security.AuthenticatedUser;
import com.consoleadda.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stock is a ledger: every movement is an insert-only {@code inventory_transactions} row, and
 * {@code food_items.stock_quantity} is a cache updated atomically in the same transaction.
 * <pre>closing = opening + purchases − sales − wastage ± adjustments</pre>
 * Operators may record purchases and wastage; opening stock and adjustments are admin-only.
 */
@Service
public class InventoryService {

    public static final String REF_BILL = "BILL";

    private final InventoryTransactionRepository txnRepository;
    private final FoodItemRepository itemRepository;
    private final BusinessDayService businessDay;
    private final SettingsService settings;
    private final AuditService auditService;
    private final JdbcTemplate jdbc;

    public InventoryService(InventoryTransactionRepository txnRepository, FoodItemRepository itemRepository,
                            BusinessDayService businessDay, SettingsService settings, AuditService auditService,
                            JdbcTemplate jdbc) {
        this.txnRepository = txnRepository;
        this.itemRepository = itemRepository;
        this.businessDay = businessDay;
        this.settings = settings;
        this.auditService = auditService;
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- manual entries

    @Transactional
    public InventoryTxnResponse record(InventoryTxnRequest request) {
        AuthenticatedUser user = CurrentUser.require();
        InventoryTxnType type = request.type();
        if (type == InventoryTxnType.SALE || type == InventoryTxnType.SALE_REVERSAL) {
            throw BusinessException.badRequest("INVALID_TXN_TYPE", "Sales are recorded automatically from bills");
        }
        if ((type == InventoryTxnType.OPENING || type == InventoryTxnType.ADJUSTMENT) && !user.isAdmin()) {
            throw BusinessException.forbidden("ACCESS_DENIED", "Only an admin can record opening stock or adjustments");
        }
        int qty = request.quantity();
        int delta = switch (type) {
            case OPENING, PURCHASE -> requirePositive(qty);
            case WASTAGE -> -requirePositive(qty);
            case ADJUSTMENT -> {
                if (qty == 0) {
                    throw BusinessException.badRequest("INVALID_QUANTITY", "Adjustment cannot be zero");
                }
                yield qty;
            }
            default -> throw new IllegalStateException();
        };
        FoodItem item = itemRepository.findById(request.foodItemId())
                .orElseThrow(() -> new NotFoundException("Menu item", request.foodItemId()));
        if (!item.isTrackInventory()) {
            throw BusinessException.badRequest("NOT_TRACKED", "'" + item.getName() + "' does not track stock");
        }
        applyDelta(item, delta);
        InventoryTransaction txn = insert(item, type, delta, request.unitCost(), null, null, request.notes(), user.id());
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("item", item.getName());
        audit.put("type", type);
        audit.put("quantity", delta);
        audit.put("notes", request.notes());
        auditService.record("INVENTORY_ADJUSTED", "FoodItem", item.getId(), null, audit);
        return toResponse(txn);
    }

    // ---------------------------------------------------------------- automatic (billing)

    /** Records a sale of {@code quantity} units for a bill. Untracked items leave no ledger entry. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sell(FoodItem item, int quantity, Long billId) {
        if (!item.isTrackInventory() || quantity <= 0) {
            return;
        }
        applyDelta(item, -quantity);
        insert(item, InventoryTxnType.SALE, -quantity, item.getCostPrice(), REF_BILL, billId, null,
                CurrentUser.get().map(AuthenticatedUser::id).orElse(null));
    }

    /** Puts stock back when a line is removed or reduced, or a paid bill is cancelled. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverseSale(FoodItem item, int quantity, Long billId) {
        if (!item.isTrackInventory() || quantity <= 0) {
            return;
        }
        itemRepository.changeStock(item.getId(), quantity);
        insert(item, InventoryTxnType.SALE_REVERSAL, quantity, item.getCostPrice(), REF_BILL, billId, null,
                CurrentUser.get().map(AuthenticatedUser::id).orElse(null));
    }

    // ---------------------------------------------------------------- reports

    @Transactional(readOnly = true)
    public List<InventoryTxnResponse> transactions(Long itemId, LocalDate from, LocalDate to, int limit) {
        return txnRepository.search(itemId, from, to, PageRequest.of(0, Math.min(Math.max(limit, 1), 1000)))
                .stream().map(InventoryService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<StockRow> stock(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw BusinessException.badRequest("INVALID_DATES", "'To' must be on or after 'from'");
        }
        Map<Long, Map<String, long[]>> agg = new HashMap<>();
        jdbc.query("""
                SELECT food_item_id, txn_type,
                       SUM(CASE WHEN business_date < ? THEN quantity ELSE 0 END) AS before_range,
                       SUM(CASE WHEN business_date >= ? THEN quantity ELSE 0 END) AS in_range
                FROM inventory_transactions WHERE business_date <= ?
                GROUP BY food_item_id, txn_type""",
                rs -> {
                    agg.computeIfAbsent(rs.getLong("food_item_id"), k -> new HashMap<>())
                            .put(rs.getString("txn_type"), new long[]{rs.getLong("before_range"), rs.getLong("in_range")});
                }, from, from, to);
        return itemRepository.findAllOrdered().stream().map(item -> {
            Map<String, long[]> m = agg.getOrDefault(item.getId(), Map.of());
            long before = m.values().stream().mapToLong(v -> v[0]).sum();
            long opening = before + in(m, "OPENING");
            long purchases = in(m, "PURCHASE");
            long sales = -(in(m, "SALE") + in(m, "SALE_REVERSAL"));
            long wastage = -in(m, "WASTAGE");
            long adjustments = in(m, "ADJUSTMENT");
            long closing = opening + purchases - sales - wastage + adjustments;
            boolean low = item.isTrackInventory() && item.getReorderLevel() > 0
                    && item.getStockQuantity() <= item.getReorderLevel();
            return new StockRow(item.getId(), item.getName(), item.getCategory().getName(), item.isTrackInventory(),
                    opening, purchases, sales, wastage, adjustments, closing, item.getStockQuantity(),
                    item.getReorderLevel(), low);
        }).toList();
    }

    // ---------------------------------------------------------------- helpers

    private void applyDelta(FoodItem item, int delta) {
        boolean allowNegative = settings.getBoolean(SettingKeys.ALLOW_NEGATIVE_STOCK, true);
        int updated = allowNegative || delta >= 0
                ? itemRepository.changeStock(item.getId(), delta)
                : itemRepository.changeStockIfAvailable(item.getId(), delta);
        if (updated == 0) {
            int available = jdbc.queryForObject("SELECT stock_quantity FROM food_items WHERE id = ?", Integer.class,
                    item.getId());
            throw BusinessException.conflict("OUT_OF_STOCK", "Not enough stock of '" + item.getName() + "' (only "
                    + Math.max(available, 0) + " left)");
        }
    }

    private InventoryTransaction insert(FoodItem item, InventoryTxnType type, int quantity, java.math.BigDecimal unitCost,
                                        String refType, Long refId, String notes, Long userId) {
        InventoryTransaction txn = new InventoryTransaction();
        txn.setFoodItem(item);
        txn.setTxnType(type);
        txn.setQuantity(quantity);
        txn.setUnitCost(unitCost);
        txn.setReferenceType(refType);
        txn.setReferenceId(refId);
        txn.setNotes(notes);
        txn.setBusinessDate(businessDay.today());
        txn.setCreatedBy(userId);
        return txnRepository.save(txn);
    }

    private static long in(Map<String, long[]> m, String type) {
        long[] v = m.get(type);
        return v == null ? 0 : v[1];
    }

    private static int requirePositive(int qty) {
        if (qty <= 0) {
            throw BusinessException.badRequest("INVALID_QUANTITY", "Quantity must be greater than zero");
        }
        return qty;
    }

    private static InventoryTxnResponse toResponse(InventoryTransaction t) {
        return new InventoryTxnResponse(t.getId(), t.getFoodItem().getId(), t.getFoodItem().getName(), t.getTxnType(),
                t.getQuantity(), t.getUnitCost(), t.getReferenceType(), t.getReferenceId(), t.getNotes(),
                t.getBusinessDate(), t.getCreatedAt());
    }
}
