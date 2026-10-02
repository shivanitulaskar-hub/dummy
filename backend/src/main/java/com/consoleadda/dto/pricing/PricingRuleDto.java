package com.consoleadda.dto.pricing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PricingRuleDto(
        @NotNull @Min(1) @Max(50) Integer playerCount,
        @NotNull @Min(1) @Max(1440) Integer durationMinutes,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal price
) {
}
