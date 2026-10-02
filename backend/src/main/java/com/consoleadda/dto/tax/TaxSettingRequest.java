package com.consoleadda.dto.tax;

import com.consoleadda.service.billing.PriceMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record TaxSettingRequest(
        @NotNull Boolean enabled,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal ratePercent,
        @NotNull PriceMode priceMode,
        @Pattern(regexp = "^[0-9]{0,10}$", message = "digits only, up to 10") String hsnSacCode
) {
}
