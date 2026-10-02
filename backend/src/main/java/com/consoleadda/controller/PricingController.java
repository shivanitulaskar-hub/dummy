package com.consoleadda.controller;

import com.consoleadda.dto.pricing.PriceQuoteResponse;
import com.consoleadda.dto.pricing.PricingPlanRequest;
import com.consoleadda.dto.pricing.PricingPlanResponse;
import com.consoleadda.service.PricingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Pricing master. Everyone can read prices (the start dialog shows the plan); only admins change them. */
@Validated
@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping
    public List<PricingPlanResponse> list() {
        return pricingService.list();
    }

    @GetMapping("/current")
    public PricingPlanResponse current() {
        return pricingService.current();
    }

    @GetMapping("/{id}")
    public PricingPlanResponse get(@PathVariable Long id) {
        return pricingService.get(id);
    }

    /** Preview a price, e.g. GET /api/pricing/quote?minutes=95&players=3 */
    @GetMapping("/quote")
    public PriceQuoteResponse quote(@RequestParam(required = false) Long planId,
                                    @RequestParam @Min(0) @Max(10080) int minutes,
                                    @RequestParam(defaultValue = "0") @Min(0) @Max(1440) int creditMinutes,
                                    @RequestParam(defaultValue = "1") @Min(1) @Max(50) int players) {
        return pricingService.quote(planId, minutes, creditMinutes, players);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public PricingPlanResponse create(@Valid @RequestBody PricingPlanRequest request) {
        return pricingService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PricingPlanResponse update(@PathVariable Long id, @Valid @RequestBody PricingPlanRequest request) {
        return pricingService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        pricingService.delete(id);
    }
}
