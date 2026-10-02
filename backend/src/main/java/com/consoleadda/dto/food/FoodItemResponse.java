package com.consoleadda.dto.food;

import java.math.BigDecimal;

/** {@code costPrice} is only included for admins. */
public record FoodItemResponse(
        Long id,
        String name,
        Long categoryId,
        String categoryName,
        BigDecimal sellingPrice,
        BigDecimal costPrice,
        int stockQuantity,
        int reorderLevel,
        boolean lowStock,
        boolean trackInventory,
        int gamingCreditMinutes,
        boolean active
) {
}
