package com.consoleadda.service;

import com.consoleadda.entity.Discount;
import com.consoleadda.service.billing.BillCalculator;
import org.springframework.stereotype.Service;

import java.util.List;

/** Applies the configured GST rules and rounding setting to bill lines via the pure {@link BillCalculator}. */
@Service
public class BillTotalsService {

    private final TaxRuleService taxRules;
    private final SettingsService settings;

    public BillTotalsService(TaxRuleService taxRules, SettingsService settings) {
        this.taxRules = taxRules;
        this.settings = settings;
    }

    public BillCalculator.Result calculate(List<BillCalculator.Line> lines, Discount discount) {
        BillCalculator.Discount spec = discount == null ? null
                : new BillCalculator.Discount(discount.getDiscountType(), discount.getValue(), discount.getMaxAmount());
        return BillCalculator.calculate(lines, spec, taxRules.rules(),
                settings.getBoolean(SettingKeys.ROUND_TO_RUPEE, true));
    }
}
