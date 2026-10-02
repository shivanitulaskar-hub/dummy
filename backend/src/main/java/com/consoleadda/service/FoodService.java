package com.consoleadda.service;

import com.consoleadda.dto.food.FoodCategoryRequest;
import com.consoleadda.dto.food.FoodCategoryResponse;
import com.consoleadda.dto.food.FoodItemRequest;
import com.consoleadda.dto.food.FoodItemResponse;
import com.consoleadda.entity.FoodCategory;
import com.consoleadda.entity.FoodItem;
import com.consoleadda.exception.BusinessException;
import com.consoleadda.exception.NotFoundException;
import com.consoleadda.repository.FoodCategoryRepository;
import com.consoleadda.repository.FoodItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Food & beverage master: menu items and categories are data, never code. */
@Service
public class FoodService {

    private final FoodItemRepository itemRepository;
    private final FoodCategoryRepository categoryRepository;
    private final AuditService auditService;

    public FoodService(FoodItemRepository itemRepository, FoodCategoryRepository categoryRepository,
                       AuditService auditService) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.auditService = auditService;
    }

    // ---------------------------------------------------------------- categories

    @Transactional(readOnly = true)
    public List<FoodCategoryResponse> categories() {
        return categoryRepository.findAllByOrderByDisplayOrderAscNameAsc().stream().map(FoodService::toResponse).toList();
    }

    @Transactional
    public FoodCategoryResponse createCategory(FoodCategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw BusinessException.conflict("DUPLICATE_NAME", "Category '" + name + "' already exists");
        }
        FoodCategory category = new FoodCategory();
        applyCategory(category, request, name);
        categoryRepository.saveAndFlush(category);
        auditService.record("FOOD_CATEGORY_CREATED", "FoodCategory", category.getId(), null, toResponse(category));
        return toResponse(category);
    }

    @Transactional
    public FoodCategoryResponse updateCategory(Long id, FoodCategoryRequest request) {
        FoodCategory category = categoryRepository.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw BusinessException.conflict("DUPLICATE_NAME", "Category '" + name + "' already exists");
        }
        FoodCategoryResponse before = toResponse(category);
        applyCategory(category, request, name);
        categoryRepository.saveAndFlush(category);
        auditService.record("FOOD_CATEGORY_UPDATED", "FoodCategory", id, before, toResponse(category));
        return toResponse(category);
    }

    // ---------------------------------------------------------------- items

    @Transactional(readOnly = true)
    public List<FoodItemResponse> items(boolean includeInactive, boolean includeCost) {
        return itemRepository.findAllOrdered().stream()
                .filter(i -> includeInactive || (i.isActive() && i.getCategory().isActive()))
                .map(i -> toResponse(i, includeCost)).toList();
    }

    @Transactional
    public FoodItemResponse createItem(FoodItemRequest request) {
        String name = request.name().trim();
        if (itemRepository.existsByNameIgnoreCase(name)) {
            throw BusinessException.conflict("DUPLICATE_NAME", "Menu item '" + name + "' already exists");
        }
        FoodItem item = new FoodItem();
        applyItem(item, request, name);
        itemRepository.saveAndFlush(item);
        FoodItemResponse response = toResponse(item, true);
        auditService.record("FOOD_ITEM_CREATED", "FoodItem", item.getId(), null, response);
        return response;
    }

    @Transactional
    public FoodItemResponse updateItem(Long id, FoodItemRequest request) {
        FoodItem item = itemRepository.findById(id).orElseThrow(() -> new NotFoundException("Menu item", id));
        String name = request.name().trim();
        if (itemRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw BusinessException.conflict("DUPLICATE_NAME", "Menu item '" + name + "' already exists");
        }
        FoodItemResponse before = toResponse(item, true);
        applyItem(item, request, name);
        itemRepository.saveAndFlush(item);
        FoodItemResponse after = toResponse(item, true);
        boolean priceChanged = before.sellingPrice().compareTo(after.sellingPrice()) != 0
                || before.costPrice().compareTo(after.costPrice()) != 0;
        auditService.record(priceChanged ? "FOOD_PRICE_CHANGED" : "FOOD_ITEM_UPDATED", "FoodItem", id, before, after);
        return after;
    }

    /** An item that can be put on a bill right now. */
    @Transactional(readOnly = true)
    public FoodItem sellable(Long id) {
        FoodItem item = itemRepository.findById(id).orElseThrow(() -> new NotFoundException("Menu item", id));
        if (!item.isActive() || !item.getCategory().isActive()) {
            throw BusinessException.conflict("ITEM_INACTIVE", "'" + item.getName() + "' is not on the menu any more");
        }
        return item;
    }

    private void applyItem(FoodItem item, FoodItemRequest r, String name) {
        FoodCategory category = categoryRepository.findById(r.categoryId())
                .orElseThrow(() -> new NotFoundException("Category", r.categoryId()));
        item.setName(name);
        item.setCategory(category);
        item.setSellingPrice(r.sellingPrice());
        item.setCostPrice(r.costPrice());
        item.setReorderLevel(r.reorderLevel());
        item.setTrackInventory(r.trackInventory());
        item.setGamingCreditMinutes(r.gamingCreditMinutes());
        item.setActive(r.active());
    }

    private static void applyCategory(FoodCategory c, FoodCategoryRequest r, String name) {
        c.setName(name);
        if (r.displayOrder() != null) {
            c.setDisplayOrder(r.displayOrder());
        }
        if (r.active() != null) {
            c.setActive(r.active());
        }
    }

    public static FoodItemResponse toResponse(FoodItem i, boolean includeCost) {
        // Warn only once an admin has set a reorder level; untouched items (level 0) never nag the counter.
        boolean low = i.isTrackInventory() && i.getReorderLevel() > 0 && i.getStockQuantity() <= i.getReorderLevel();
        return new FoodItemResponse(i.getId(), i.getName(), i.getCategory().getId(), i.getCategory().getName(),
                i.getSellingPrice(), includeCost ? i.getCostPrice() : null, i.getStockQuantity(), i.getReorderLevel(),
                low, i.isTrackInventory(), i.getGamingCreditMinutes(), i.isActive());
    }

    private static FoodCategoryResponse toResponse(FoodCategory c) {
        return new FoodCategoryResponse(c.getId(), c.getName(), c.getDisplayOrder(), c.isActive());
    }

    static Map<String, Object> describe(FoodItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("item", item.getName());
        m.put("price", item.getSellingPrice());
        return m;
    }
}
