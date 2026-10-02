package com.consoleadda.controller;

import com.consoleadda.dto.discount.DiscountRequest;
import com.consoleadda.dto.discount.DiscountResponse;
import com.consoleadda.security.AuthenticatedUser;
import com.consoleadda.service.DiscountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Operators see the discounts usable today (to apply at billing); admins see and manage all. */
@RestController
@RequestMapping("/api/discounts")
public class DiscountController {

    private final DiscountService discountService;

    public DiscountController(DiscountService discountService) {
        this.discountService = discountService;
    }

    @GetMapping
    public List<DiscountResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return discountService.list(!user.isAdmin());
    }

    @GetMapping("/usable")
    public List<DiscountResponse> usable() {
        return discountService.list(true);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public DiscountResponse create(@Valid @RequestBody DiscountRequest request) {
        return discountService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DiscountResponse update(@PathVariable Long id, @Valid @RequestBody DiscountRequest request) {
        return discountService.update(id, request);
    }
}
