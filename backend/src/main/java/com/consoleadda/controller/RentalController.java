package com.consoleadda.controller;

import com.consoleadda.dto.PageResponse;
import com.consoleadda.dto.rental.CancelRentalRequest;
import com.consoleadda.dto.rental.RentalRequest;
import com.consoleadda.dto.rental.RentalResponse;
import com.consoleadda.dto.rental.ReturnPreview;
import com.consoleadda.dto.rental.ReturnRentalRequest;
import com.consoleadda.entity.RentalStatus;
import com.consoleadda.service.RentalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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

@Validated
@RestController
@RequestMapping("/api/rentals")
public class RentalController {

    private final RentalService rentalService;

    public RentalController(RentalService rentalService) {
        this.rentalService = rentalService;
    }

    /** Rent a disc out; the rental charge (and deposit) are paid now. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RentalResponse create(@Valid @RequestBody RentalRequest request,
                                 @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 64)
                                 String idempotencyKey) {
        return rentalService.create(request, idempotencyKey);
    }

    /** {@code status}: OUT (incl. overdue), OVERDUE, RETURNED, CANCELLED; omit for all. */
    @GetMapping
    public PageResponse<RentalResponse> search(@RequestParam(required = false) RentalStatus status,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "25") int size) {
        return rentalService.search(status, q, page, size);
    }

    @GetMapping("/{id}")
    public RentalResponse get(@PathVariable Long id) {
        return rentalService.get(id);
    }

    /** Late fee and deposit refund if the disc is returned now. Changes nothing. */
    @GetMapping("/{id}/return-preview")
    public ReturnPreview returnPreview(@PathVariable Long id) {
        return rentalService.previewReturn(id);
    }

    @PostMapping("/{id}/return")
    public RentalResponse returnGame(@PathVariable Long id, @Valid @RequestBody(required = false) ReturnRentalRequest request) {
        return rentalService.returnGame(id, request);
    }

    /** Operators within the cancel window after renting out; admins any time while the disc is out. */
    @PostMapping("/{id}/cancel")
    public RentalResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRentalRequest request) {
        return rentalService.cancel(id, request);
    }
}
