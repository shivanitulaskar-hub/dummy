package com.consoleadda.controller;

import com.consoleadda.dto.inventory.InventoryTxnRequest;
import com.consoleadda.dto.inventory.InventoryTxnResponse;
import com.consoleadda.dto.inventory.StockRow;
import com.consoleadda.service.BusinessDayService;
import com.consoleadda.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Stock ledger. Purchases and wastage can be recorded by any user; opening stock and adjustments by admins
 * (enforced in {@link InventoryService}).
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final BusinessDayService businessDay;

    public InventoryController(InventoryService inventoryService, BusinessDayService businessDay) {
        this.inventoryService = inventoryService;
        this.businessDay = businessDay;
    }

    /** Opening, purchases, sales, wastage, adjustments and closing per item; defaults to today. */
    @GetMapping("/stock")
    public List<StockRow> stock(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = businessDay.today();
        return inventoryService.stock(from == null ? today : from, to == null ? today : to);
    }

    @GetMapping("/transactions")
    public List<InventoryTxnResponse> transactions(
            @RequestParam(required = false) Long foodItemId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "200") int limit) {
        LocalDate today = businessDay.today();
        return inventoryService.transactions(foodItemId, from == null ? today.minusDays(30) : from,
                to == null ? today : to, limit);
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryTxnResponse record(@Valid @RequestBody InventoryTxnRequest request) {
        return inventoryService.record(request);
    }
}
