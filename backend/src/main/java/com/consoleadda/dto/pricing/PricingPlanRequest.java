package com.consoleadda.dto.pricing;

import com.consoleadda.entity.DayType;
import com.consoleadda.service.billing.AdditionalPlayerBasis;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PricingPlanRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull DayType dayType,
        @NotNull LocalDate activeFrom,
        LocalDate activeTo,
        @NotNull Boolean active,
        @NotNull @Min(0) @Max(1000) Integer priority,
        @NotNull @Min(1) @Max(1440) Integer minBillableMinutes,
        @NotNull @Min(1) @Max(240) Integer billingBlockMinutes,
        @NotNull @Min(0) @Max(239) Integer graceMinutes,
        @NotNull Boolean graceEveryBoundary,
        @NotNull @Min(1) @Max(240) Integer extensionBlockMinutes,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal extensionPrice,
        @NotNull @DecimalMin(value = "0.00", message = "must not be negative") @Digits(integer = 10, fraction = 2)
        BigDecimal additionalPlayerPrice,
        @NotNull AdditionalPlayerBasis additionalPlayerBasis,
        @NotNull @Min(1) @Max(50) Integer maxPlayers,
        @Size(max = 500) String notes,
        @NotEmpty @Size(max = 200) List<@Valid @NotNull PricingRuleDto> rules
) {
}
