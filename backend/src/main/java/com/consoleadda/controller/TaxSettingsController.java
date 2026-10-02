package com.consoleadda.controller;

import com.consoleadda.dto.tax.TaxSettingRequest;
import com.consoleadda.dto.tax.TaxSettingResponse;
import com.consoleadda.service.TaxRuleService;
import com.consoleadda.service.billing.TaxCategory;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GST configuration per category (admin only; also protected by the /api/settings/** URL rule). */
@RestController
@RequestMapping("/api/settings/tax")
@PreAuthorize("hasRole('ADMIN')")
public class TaxSettingsController {

    private final TaxRuleService taxRuleService;

    public TaxSettingsController(TaxRuleService taxRuleService) {
        this.taxRuleService = taxRuleService;
    }

    @GetMapping
    public List<TaxSettingResponse> list() {
        return taxRuleService.list();
    }

    @PutMapping("/{category}")
    public TaxSettingResponse update(@PathVariable TaxCategory category, @Valid @RequestBody TaxSettingRequest request) {
        return taxRuleService.update(category, request);
    }
}
