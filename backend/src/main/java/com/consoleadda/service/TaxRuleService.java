package com.consoleadda.service;

import com.consoleadda.dto.tax.TaxSettingRequest;
import com.consoleadda.dto.tax.TaxSettingResponse;
import com.consoleadda.entity.TaxSetting;
import com.consoleadda.exception.NotFoundException;
import com.consoleadda.repository.TaxSettingRepository;
import com.consoleadda.service.billing.BillCalculator;
import com.consoleadda.service.billing.TaxCategory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** GST configuration per category. Nothing about tax is hard-coded. */
@Service
public class TaxRuleService {

    private final TaxSettingRepository repository;
    private final AuditService auditService;

    public TaxRuleService(TaxSettingRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Map<TaxCategory, TaxSetting> settings() {
        Map<TaxCategory, TaxSetting> map = new EnumMap<>(TaxCategory.class);
        repository.findAll().forEach(t -> map.put(t.getCategory(), t));
        return map;
    }

    @Transactional(readOnly = true)
    public Map<TaxCategory, BillCalculator.TaxRule> rules() {
        Map<TaxCategory, BillCalculator.TaxRule> rules = new EnumMap<>(TaxCategory.class);
        settings().forEach((c, t) -> rules.put(c,
                new BillCalculator.TaxRule(t.isEnabled(), t.getRatePercent(), t.getPriceMode())));
        return rules;
    }

    @Transactional(readOnly = true)
    public List<TaxSettingResponse> list() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(TaxSetting::getCategory))
                .map(TaxRuleService::toResponse).toList();
    }

    @Transactional
    public TaxSettingResponse update(TaxCategory category, TaxSettingRequest request) {
        TaxSetting setting = settings().get(category);
        if (setting == null) {
            throw new NotFoundException("Tax setting", category);
        }
        TaxSettingResponse before = toResponse(setting);
        setting.setEnabled(request.enabled());
        setting.setRatePercent(request.ratePercent());
        setting.setPriceMode(request.priceMode());
        setting.setHsnSacCode(request.hsnSacCode() == null || request.hsnSacCode().isBlank() ? null : request.hsnSacCode());
        repository.saveAndFlush(setting);
        TaxSettingResponse after = toResponse(setting);
        auditService.record("GST_CHANGED", "TaxSetting", category, before, after);
        return after;
    }

    private static TaxSettingResponse toResponse(TaxSetting t) {
        return new TaxSettingResponse(t.getCategory(), t.isEnabled(), t.getRatePercent(), t.getPriceMode(),
                t.getHsnSacCode());
    }
}
