package com.consoleadda.dto.inventory;

/**
 * Stock movement for one item over a date range:
 * closing = opening + purchases − sales − wastage + adjustments (adjustments are signed).
 */
public record StockRow(
        Long foodItemId,
        String name,
        String category,
        boolean trackInventory,
        long opening,
        long purchases,
        long sales,
        long wastage,
        long adjustments,
        long closing,
        int currentStock,
        int reorderLevel,
        boolean lowStock
) {
}
