package com.consoleadda.dto.inventory;

import com.consoleadda.entity.InventoryTxnType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Manual stock entry. {@code quantity} is always entered as a positive count for OPENING, PURCHASE and WASTAGE
 * (the sign is applied by type); ADJUSTMENT takes a signed correction (e.g. −2 after a stock count).
 */
public record InventoryTxnRequest(
        @NotNull Long foodItemId,
        @NotNull InventoryTxnType type,
        @NotNull @Min(-100000) @Max(100000) Integer quantity,
        @DecimalMin(value = "0.00", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal unitCost,
        @Size(max = 255) String notes
) {
}
