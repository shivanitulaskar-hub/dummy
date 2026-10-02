package com.consoleadda.controller;

import com.consoleadda.dto.PageResponse;
import com.consoleadda.dto.bill.BillDetail;
import com.consoleadda.dto.bill.BillSummary;
import com.consoleadda.dto.bill.CancelBillRequest;
import com.consoleadda.dto.bill.InvoiceResponse;
import com.consoleadda.dto.bill.WalkInBillRequest;
import com.consoleadda.entity.BillStatus;
import com.consoleadda.service.BillService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    /** Walk-in food/drink sale, created and paid in one step. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillDetail createWalkIn(@Valid @RequestBody WalkInBillRequest request,
                                   @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 64)
                                   String idempotencyKey) {
        return billService.createWalkIn(request, idempotencyKey);
    }

    /** Search by bill number, customer name or mobile; paid and cancelled bills by default. */
    @GetMapping
    public PageResponse<BillSummary> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BillStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return billService.search(q, from, to, status, page, size);
    }

    @GetMapping("/{id}")
    public BillDetail get(@PathVariable Long id) {
        return billService.get(id);
    }

    @GetMapping("/{id}/invoice")
    public InvoiceResponse invoice(@PathVariable Long id) {
        return billService.invoice(id);
    }

    /** Cancel a paid bill with full refund (admin only). */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public BillDetail cancel(@PathVariable Long id, @Valid @RequestBody CancelBillRequest request) {
        return billService.cancel(id, request);
    }
}
