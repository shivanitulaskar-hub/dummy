package com.consoleadda.dto.inventory;

import com.consoleadda.entity.InventoryTxnType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InventoryTxnResponse(Long id, Long foodItemId, String foodItemName, InventoryTxnType type, int quantity,
                                   BigDecimal unitCost, String referenceType, Long referenceId, String notes,
                                   LocalDate businessDate, Instant createdAt) {
}
