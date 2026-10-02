package com.consoleadda.dto.tax;

import com.consoleadda.service.billing.PriceMode;
import com.consoleadda.service.billing.TaxCategory;

import java.math.BigDecimal;

public record TaxSettingResponse(TaxCategory category, boolean enabled, BigDecimal ratePercent, PriceMode priceMode,
                                 String hsnSacCode) {
}
